import axios from "axios";
import crypto from "node:crypto";
import { env } from "../config";

const client = axios.create({
  baseURL: env.IAK_BASE_URL,
  timeout: 30_000,
  headers: { "Content-Type": "application/json" }
});

function md5(value: string): string {
  return crypto.createHash("md5").update(value).digest("hex");
}

function unwrap(response: any): any {
  return response?.data ?? response;
}

export async function inquiryPln(customerNo: string, refId: string) {
  const sign = md5(`${env.IAK_USERNAME}${env.IAK_API_KEY}${refId}`);
  const response = await client.post("", {
    commands: "inq-pasca",
    username: env.IAK_USERNAME,
    code: env.IAK_PLN_PRODUCT_CODE,
    hp: customerNo,
    ref_id: refId,
    sign
  });
  return response.data;
}

export async function payPasca(trId: number) {
  if (!Number.isInteger(trId) || trId <= 0) {
    throw new Error("IAK inquiry transaction ID (tr_id) tidak valid");
  }

  const sign = md5(`${env.IAK_USERNAME}${env.IAK_API_KEY}${trId}`);
  const response = await client.post("", {
    commands: "pay-pasca",
    username: env.IAK_USERNAME,
    tr_id: trId,
    sign
  });
  return response.data;
}

export async function statusPasca(refId: string) {
  const sign = md5(`${env.IAK_USERNAME}${env.IAK_API_KEY}cs`);
  const response = await client.post("", {
    commands: "checkstatus",
    username: env.IAK_USERNAME,
    ref_id: refId,
    sign
  });
  return response.data;
}

export function getIakData(response: any): any {
  return unwrap(response);
}
