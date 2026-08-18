import { BillingCategory } from "@prisma/client";
import { env } from "../config";

export function todayInJakarta(now = new Date()): { year: number; month: number; day: number } {
  if (env.BILLING_AS_OF_DATE) {
    const [year, month, day] = env.BILLING_AS_OF_DATE.split("-").map(Number);
    return { year, month, day };
  }
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: env.BILLING_TIMEZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit"
  }).formatToParts(now);
  const get = (type: string) => Number(parts.find(p => p.type === type)?.value);
  return { year: get("year"), month: get("month"), day: get("day") };
}

export function currentPeriodJakarta(now = new Date()): string {
  const d = todayInJakarta(now);
  return `${d.year}-${String(d.month).padStart(2, "0")}`;
}

export function classifyBilling(period: string, now = new Date()): BillingCategory {
  const [year, month] = period.split("-").map(Number);
  if (!year || !month || month < 1 || month > 12) throw new Error("Invalid billing period");
  const current = todayInJakarta(now);

  if (year < current.year || (year === current.year && month < current.month)) return BillingCategory.IRISAN;
  if (year === current.year && month === current.month) return current.day <= 20 ? BillingCategory.PREVENTIF : BillingCategory.KOREKTIF;
  return BillingCategory.PREVENTIF;
}

export function dueDateFor(period: string): Date {
  const [year, month] = period.split("-").map(Number);
  return new Date(Date.UTC(year, month - 1, 20, 16, 59, 59, 999));
}
