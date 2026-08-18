import crypto from "node:crypto";

export function md5(value: string): string {
  return crypto.createHash("md5").update(value).digest("hex");
}

export function verifyWebhookSignature(rawBody: Buffer, header: string | undefined, secret: string): boolean {
  if (!header?.startsWith("sha1=")) return false;
  const received = header.slice(5);
  const expected = crypto.createHmac("sha1", secret).update(rawBody).digest("hex");
  const a = Buffer.from(received, "utf8");
  const b = Buffer.from(expected, "utf8");
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

export function makeRefId(prefix = "PLN"): string {
  return `${prefix}-${Date.now()}-${crypto.randomBytes(4).toString("hex")}`;
}
