import { Router } from "express";
import { prisma } from "../lib/prisma";
import { auth } from "../middleware/auth";
const router=Router(); router.use(auth);
router.get("/:invoiceNo",async(req,res,next)=>{try{const invoice=await prisma.invoice.findUnique({where:{invoiceNo:req.params.invoiceNo},include:{payment:{include:{customer:{include:{region:true,ulp:true}}}}}});if(!invoice)return res.status(404).json({error:"Invoice tidak ditemukan"});res.json(invoice);}catch(e){next(e)}});
export default router;
