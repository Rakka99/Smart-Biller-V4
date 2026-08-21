import { Router } from "express";
import { z } from "zod";
import { prisma } from "../lib/prisma";
import { auth } from "../middleware/auth";
import { payPasca, statusPasca } from "../services/iak";
import { classifyBilling, dueDateFor } from "../services/billing-classifier";

const router = Router();
router.use(auth);

function mapIakStatus(data: any): "SUCCESS" | "FAILED" | "PENDING" | "UNKNOWN" {
  const rc = String(data?.response_code ?? data?.rc ?? "");
  const status = Number(data?.status);
  if (rc === "00" || status === 1) return "SUCCESS";
  if (rc === "39" || status === 3 || status === 0) return "PENDING";
  if (rc) return "FAILED";
  return "UNKNOWN";
}

router.post("/", async (req, res, next) => {
  try {
    const { inquiryId } = z.object({ inquiryId: z.string().min(1) }).parse(req.body);
    const inquiry = await prisma.inquiry.findUnique({ where: { id: inquiryId }, include: { customer: true } });

    if (!inquiry) return res.status(404).json({ error: "Inquiry tidak ditemukan" });
    if (inquiry.status !== "SUCCESS") return res.status(400).json({ error: "Inquiry tidak valid" });

    const existing = await prisma.payment.findUnique({ where: { refId: inquiry.refId } });
    if (existing) return res.json({ payment: existing, reused: true });

    const rawInquiry: any = inquiry.rawResponse ?? {};
    const trId = Number(rawInquiry?.data?.tr_id ?? rawInquiry?.tr_id);
    if (!Number.isInteger(trId) || trId <= 0) {
      return res.status(422).json({ error: "IAK inquiry tidak memiliki tr_id yang valid" });
    }

    const payment = await prisma.payment.create({
      data: {
        refId: inquiry.refId,
        inquiryId: inquiry.id,
        customerId: inquiry.customerId,
        userId: req.user!.id,
        status: "PENDING",
        amount: inquiry.amount,
        adminFee: inquiry.adminFee,
        sellingPrice: inquiry.total,
        period: inquiry.period
      }
    });

    let response: any;
    try {
      response = await payPasca(trId);
    } catch (error) {
      await prisma.payment.update({
        where: { id: payment.id },
        data: { status: "UNKNOWN", message: "IAK tidak merespons", lastStatusAt: new Date() }
      });
      throw error;
    }

    const data = response?.data ?? response;
    const status = mapIakStatus(data);
    const updated = await prisma.payment.update({
      where: { id: payment.id },
      data: {
        status,
        message: data?.message,
        rc: data?.response_code,
        serialNumber: data?.sn ?? data?.noref,
        amount: Number.isFinite(Number(data?.nominal)) ? Number(data.nominal) : payment.amount,
        adminFee: Number.isFinite(Number(data?.admin)) ? Number(data.admin) : payment.adminFee,
        sellingPrice: Number.isFinite(Number(data?.price)) ? Number(data.price) : payment.sellingPrice,
        period: data?.period ?? payment.period,
        rawResponse: response,
        paidAt: status === "SUCCESS" ? new Date() : null,
        lastStatusAt: new Date()
      }
    });

    if (status === "SUCCESS") {
      const invoiceNo = `INV-${new Date().toISOString().slice(0, 10).replace(/-/g, "")}-${updated.refId.replace(/[^A-Za-z0-9]/g, "").slice(-10).toUpperCase()}`;
      await prisma.invoice.upsert({
        where: { paymentId: updated.id },
        update: { total: updated.sellingPrice ?? 0 },
        create: { invoiceNo, paymentId: updated.id, customerId: updated.customerId, total: updated.sellingPrice ?? 0 }
      });

      if (updated.period) {
        await prisma.billing.upsert({
          where: { customerId_period: { customerId: updated.customerId, period: updated.period } },
          update: { status: "PAID", paidAt: new Date(), amount: updated.amount ?? 0, total: updated.sellingPrice ?? 0 },
          create: {
            customerId: updated.customerId,
            period: updated.period,
            dueDate: dueDateFor(updated.period),
            amount: updated.amount ?? 0,
            penalty: 0,
            total: updated.sellingPrice ?? 0,
            status: "PAID",
            category: classifyBilling(updated.period, new Date()),
            paidAt: new Date(),
            sourceRefId: updated.refId
          }
        });
      }
    }

    await prisma.auditLog.create({
      data: {
        userId: req.user!.id,
        action: "PAYMENT_CREATE",
        transactionId: updated.id,
        ipAddress: req.ip,
        metadata: { refId: updated.refId, status, provider: "IAK", iakTrId: trId }
      }
    });

    return res.status(201).json({ payment: updated, provider: "IAK", iakTrId: trId });
  } catch (e) {
    next(e);
  }
});

router.post("/:refId/check-status", async (req, res, next) => {
  try {
    const payment = await prisma.payment.findUnique({ where: { refId: req.params.refId }, include: { customer: true } });
    if (!payment) return res.status(404).json({ error: "Transaksi tidak ditemukan" });

    const ageMs = Date.now() - payment.lastStatusAt.getTime();
    if (ageMs < 60_000) return res.status(429).json({ error: "Tunggu minimal 1 menit sebelum cek status lagi" });

    const response = await statusPasca(payment.refId);
    const data = response?.data ?? response;
    const status = mapIakStatus(data);

    const updated = await prisma.payment.update({
      where: { id: payment.id },
      data: {
        status,
        message: data?.message,
        rc: data?.response_code,
        serialNumber: data?.noref ?? payment.serialNumber,
        period: data?.period ?? payment.period,
        rawResponse: response,
        paidAt: status === "SUCCESS" ? new Date() : payment.paidAt,
        lastStatusAt: new Date()
      }
    });

    return res.json({ payment: updated, provider: "IAK" });
  } catch (e) {
    next(e);
  }
});

router.get("/", async (req, res, next) => {
  try {
    const page = Math.max(1, Number(req.query.page ?? 1));
    const limit = Math.min(100, Math.max(1, Number(req.query.limit ?? 20)));
    const status = req.query.status as "PENDING" | "SUCCESS" | "FAILED" | "UNKNOWN" | undefined;
    const where = status ? { status } : {};
    const [items, total] = await Promise.all([
      prisma.payment.findMany({ where, include: { customer: true, user: { select: { name: true } } }, orderBy: { createdAt: "desc" }, skip: (page - 1) * limit, take: limit }),
      prisma.payment.count({ where })
    ]);
    return res.json({ items, total, page, limit, provider: "IAK" });
  } catch (e) {
    next(e);
  }
});

export default router;
