import "express";

declare global {
  namespace Express {
    interface Request {
      user?: { id: string; role: "ADMIN" | "SUPERVISOR" | "BILLER"; email: string };
      rawBody?: Buffer;
    }
  }
}
