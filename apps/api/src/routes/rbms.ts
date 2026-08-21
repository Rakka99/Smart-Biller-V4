import { Router } from "express";
import { prisma } from "../lib/prisma";
import { auth } from "../middleware/auth";

const router = Router();
router.use(auth);

router.get("/", async (req, res, next) => {
  try {
    const user = req.user!;
    const where = user.role === "BILLER"
      ? { billerId: user.id, active: true }
      : user.role === "SUPERVISOR"
        ? { ulpId: user.ulpId ?? "", active: true }
        : { active: true };

    const rbms = await prisma.rBM.findMany({
      where,
      orderBy: { sequence: "asc" },
      include: {
        biller: { select: { id: true, name: true, email: true } },
        ulp: { select: { id: true, code: true, name: true } },
        _count: { select: { customers: true } }
      }
    });

    return res.json({ items: rbms });
  } catch (e) {
    next(e);
  }
});

export default router;
