import { Router } from "express";
import { z } from "zod";
import { prisma } from "../lib/prisma";
import { auth, allow } from "../middleware/auth";
const router=Router(); router.use(auth);
router.post("/master/upsert",allow("ADMIN","SUPERVISOR"),async(req,res,next)=>{try{const body=z.object({regionId:z.string().optional(),ulpId:z.string().optional(),customers:z.array(z.object({customerNo:z.string().min(6),name:z.string().optional(),tariff:z.string().optional(),segmentPower:z.string().optional(),rbm:z.string().optional(),langkah:z.number().optional(),gardu:z.string().optional(),tiang:z.string().optional(),latitude:z.number().optional(),longitude:z.number().optional()})).max(5000)}).parse(req.body);let count=0;for(const c of body.customers){await prisma.customer.upsert({where:{customerNo:c.customerNo},update:{...c,regionId:body.regionId,ulpId:body.ulpId},create:{...c,regionId:body.regionId,ulpId:body.ulpId}});count++;}res.json({imported:count});}catch(e){next(e)}});
export default router;
