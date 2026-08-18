import { Router } from "express";
import { auth, adminOnly } from "../middleware/auth";
import { prisma } from "../lib/prisma";

const router=Router();
router.use(auth);
router.get("/me", async (req,res,next)=>{try{const user=await prisma.user.findUnique({where:{id:req.user!.id},include:{region:true,ulp:true}});if(!user)return res.status(404).json({error:"User not found"});res.json({id:user.id,name:user.name,email:user.email,role:user.role,region:user.region,ulp:user.ulp});}catch(e){next(e)}});
router.get("/", adminOnly, async (_req,res,next)=>{try{const items=await prisma.user.findMany({select:{id:true,name:true,email:true,role:true,active:true,region:true,ulp:true},orderBy:{name:"asc"}});res.json({items});}catch(e){next(e)}});
export default router;
