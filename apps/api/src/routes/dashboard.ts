import { Router } from "express";
import { auth } from "../middleware/auth";
import { prisma } from "../lib/prisma";
import { getDeposit } from "../services/digiflazz";

const router = Router();
router.use(auth);
router.get("/summary", async (_req, res, next) => {
  try {
    const start = new Date(); start.setHours(0, 0, 0, 0);
    const [success,pending,failed,total,recent,depositResponse]=await Promise.all([
      prisma.payment.count({where:{status:"SUCCESS",createdAt:{gte:start}}}),
      prisma.payment.count({where:{status:"PENDING",createdAt:{gte:start}}}),
      prisma.payment.count({where:{status:"FAILED",createdAt:{gte:start}}}),
      prisma.payment.aggregate({where:{status:"SUCCESS",createdAt:{gte:start}},_sum:{sellingPrice:true}}),
      prisma.payment.findMany({take:10,orderBy:{createdAt:"desc"},include:{customer:true}}),
      getDeposit().catch(()=>null)
    ]);
    return res.json({today:{success,pending,failed,totalAmount:total._sum.sellingPrice??0},deposit:depositResponse?.data?.deposit??null,recent});
  } catch(e){next(e)}
});
export default router;
