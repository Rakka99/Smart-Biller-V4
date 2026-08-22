import { Router } from "express";
import { z } from "zod";
import { prisma } from "../lib/prisma";
import { makeRefId } from "../lib/crypto";
import { inquiryPln, getIakData } from "../services/iak";
import { auth } from "../middleware/auth";

const router = Router();
router.use(auth);

router.post("/inquiry", async (req, res, next) => {
  try {
    const { customerNo } = z.object({
      customerNo: z.string().regex(/^\d{6,20}$/)
    }).parse(req.body);

    const user = req.user!;
    const existingCustomer = await prisma.customer.findUnique({
      where: { customerNo },
      include: { rbmAssignment: true }
    });

    if (user.role === "BILLER") {
      if (!existingCustomer || existingCustomer.billerId !== user.id || !existingCustomer.rbmId) {
        return res.status(403).json({ error: "Pelanggan tidak berada pada RBM Biller ini" });
      }
    } else if (user.role === "SUPERVISOR" && existingCustomer && existingCustomer.ulpId && user.ulpId && existingCustomer.ulpId !== user.ulpId) {
      return res.status(403).json({ error: "Pelanggan berada di luar wilayah ULP Supervisor" });
    }

    const refId = makeRefId("INQ");
    const response = await inquiryPln(customerNo, refId);
    const data = getIakData(response);
    const success = String(data?.response_code ?? data?.rc ?? "") === "00";

    const customer = existingCustomer
      ? await prisma.customer.update({
          where: { id: existingCustomer.id },
          data: {
            meterNo: data?.hp ?? undefined,
            name: data?.tr_name ?? undefined,
            subscriberId: data?.hp ?? undefined,
            segmentPower: data?.desc?.daya != null ? String(data.desc.daya) : undefined
          }
        })
      : await prisma.customer.create({
          data: {
            customerNo,
            meterNo: data?.hp ?? null,
            name: data?.tr_name ?? null,
            subscriberId: data?.hp ?? null,
            segmentPower: data?.desc?.daya != null ? String(data.desc.daya) : null
          }
        });

    const inquiry = await prisma.inquiry.create({
      data: {
        refId,
        customerId: customer.id,
        status: success ? "SUCCESS" : "FAILED",
        message: data?.message,
        rc: data?.response_code,
        rawResponse: response,
        period: data?.period ?? null,
        adminFee: Number.isFinite(Number(data?.admin)) ? Number(data.admin) : null,
        amount: Number.isFinite(Number(data?.nominal)) ? Number(data.nominal) : null,
        total: Number.isFinite(Number(data?.price)) ? Number(data.price) : null
      }
    });

    let billing: any = null;
    if (success) {
      const period = data?.period ?? data?.desc?.tagihan?.detail?.[0]?.periode;
      const amount = Number(data?.nominal ?? data?.desc?.tagihan?.detail?.[0]?.nilai_tagihan);
      const admin = Number(data?.admin ?? data?.desc?.tagihan?.detail?.[0]?.admin ?? 0);
      const penalty = Number(data?.desc?.tagihan?.detail?.[0]?.denda ?? 0);
      const total = Number(data?.price ?? data?.desc?.tagihan?.detail?.[0]?.total ?? amount + admin + penalty);

      billing = data;

      if (period) {
        const { classifyBilling, dueDateFor } = await import("../services/billing-classifier");
        await prisma.billing.upsert({
          where: { customerId_period: { customerId: customer.id, period } },
          update: {
            amount: Number.isFinite(amount) ? amount : 0,
            penalty: Number.isFinite(penalty) ? penalty : 0,
            total: Number.isFinite(total) ? total : 0,
            category: classifyBilling(period),
            dueDate: dueDateFor(period),
            sourceRefId: refId,
            rawData: data
          },
          create: {
            customerId: customer.id,
            period,
            amount: Number.isFinite(amount) ? amount : 0,
            penalty: Number.isFinite(penalty) ? penalty : 0,
            total: Number.isFinite(total) ? total : 0,
            category: classifyBilling(period),
            dueDate: dueDateFor(period),
            sourceRefId: refId,
            rawData: data
          }
        });
      }
    }

    return res.json({
      inquiry,
      customer,
      billing,
      provider: "IAK",
      transactionId: data?.tr_id ?? null,
      rbm: customer.rbm,
      billerId: customer.billerId
    });
  } catch (e) {
    next(e);
  }
});

export default router;
