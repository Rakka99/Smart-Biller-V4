import express from "express";
import cors from "cors";
import helmet from "helmet";
import rateLimit from "express-rate-limit";
import { env } from "./config";
import authRoutes from "./routes/auth";
import importRoutes from "./routes/import";
import plnRoutes from "./routes/pln";
import paymentRoutes from "./routes/payments";
import dashboardRoutes from "./routes/dashboard";
import billingRoutes from "./routes/billing";
import customerRoutes from "./routes/customers";
import leaderboardRoutes from "./routes/leaderboard";
import invoiceRoutes from "./routes/invoices";
import userRoutes from "./routes/users";
import { errorHandler, notFound } from "./middleware/errors";

const app = express();

app.use(helmet());
app.use(cors({ origin: env.CORS_ORIGIN, credentials: false }));
app.use(rateLimit({ windowMs: 60_000, limit: 120 }));

app.use(express.json({
  limit: "256kb",
  verify: (req, _res, buf) => {
    req.rawBody = Buffer.from(buf);
  }
}));

app.get("/health", (_req, res) => res.json({ ok: true, service: "pln-monitoring-api" }));

app.use("/api/auth", authRoutes);
app.use("/api/import", importRoutes);
app.use("/api/pln", plnRoutes);
app.use("/api/payments", paymentRoutes);
app.use("/api/dashboard", dashboardRoutes);
app.use("/api/billing", billingRoutes);
app.use("/api/customers", customerRoutes);
app.use("/api/leaderboard", leaderboardRoutes);
app.use("/api/invoices", invoiceRoutes);
app.use("/api/users", userRoutes);

app.use(notFound);
app.use(errorHandler);

app.listen(env.PORT, () => {
  console.log(`API listening on :${env.PORT}`);
});
