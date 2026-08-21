import "dotenv/config";
import { z } from "zod";

const schema = z.object({
  NODE_ENV: z.enum(["development", "test", "production"]).default("development"),
  PORT: z.coerce.number().default(4000),
  DATABASE_URL: z.string().min(1),
  JWT_SECRET: z.string().min(32),
  CORS_ORIGIN: z.string().default("http://localhost:5173"),
  IAK_BASE_URL: z.string().url().default("https://testpostpaid.mobilepulsa.net/api/v1/bill/check"),
  IAK_USERNAME: z.string().min(1),
  IAK_API_KEY: z.string().min(1),
  IAK_PLN_PRODUCT_CODE: z.string().default("PLNPOSTPAID"),
  BILLING_TIMEZONE: z.string().default("Asia/Jakarta"),
  BILLING_AS_OF_DATE: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional().or(z.literal(""))
});

export const env = schema.parse(process.env);
