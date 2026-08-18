import { NextFunction, Request, Response } from "express";
import jwt from "jsonwebtoken";
import { env } from "../config";

export function auth(req: Request, res: Response, next: NextFunction) {
  const header = req.header("authorization");
  if (!header?.startsWith("Bearer ")) return res.status(401).json({ error: "Unauthorized" });
  try {
    req.user = jwt.verify(header.slice(7), env.JWT_SECRET) as Express.Request["user"];
    return next();
  } catch {
    return res.status(401).json({ error: "Invalid or expired token" });
  }
}

export function allow(...roles: Array<"ADMIN" | "SUPERVISOR" | "BILLER">) {
  return (req: Request, res: Response, next: NextFunction) => {
    if (!req.user || !roles.includes(req.user.role)) return res.status(403).json({ error: "Forbidden" });
    next();
  };
}

export const adminOnly = allow("ADMIN");
