import axios from "axios";
import { env } from "../config";
import { md5 } from "../lib/crypto";

const client = axios.create({
  baseURL: env.DIGIFLAZZ_BASE_URL,
  timeout: 15000,
  headers: { "Content-Type": "application/json" }
});

type DigiflazzEnvelope = { data?: any; [key: string]: any };

export async function inquiryPln(customerNo: string, refId: string) {
  const payload = {
    username: env.DIGIFLAZZ_USERNAME,
    customer_no: customerNo,
    sign: md5(env.DIGIFLAZZ_USERNAME + env.DIGIFLAZZ_API_KEY + customerNo)
  };
  const response = await client.post<DigiflazzEnvelope>("/inquiry-pln", payload);
  return response.data;
}

export async function inquiryPasca(customerNo: string, refId: string) {
  const payload = {
    commands: "inq-pasca",
    username: env.DIGIFLAZZ_USERNAME,
    buyer_sku_code: env.DIGIFLAZZ_PLN_SKU,
    customer_no: customerNo,
    ref_id: refId,
    sign: md5(env.DIGIFLAZZ_USERNAME + env.DIGIFLAZZ_API_KEY + refId),
    testing: env.DIGIFLAZZ_TESTING
  };
  const response = await client.post<DigiflazzEnvelope>("/transaction", payload);
  return response.data;
}

export async function payPasca(customerNo: string, refId: string) {
  const payload = {
    commands: "pay-pasca",
    username: env.DIGIFLAZZ_USERNAME,
    buyer_sku_code: env.DIGIFLAZZ_PLN_SKU,
    customer_no: customerNo,
    ref_id: refId,
    sign: md5(env.DIGIFLAZZ_USERNAME + env.DIGIFLAZZ_API_KEY + refId),
    testing: env.DIGIFLAZZ_TESTING
  };
  const response = await client.post<DigiflazzEnvelope>("/transaction", payload);
  return response.data;
}

export async function statusPasca(customerNo: string, refId: string) {
  const payload = {
    commands: "status-pasca",
    username: env.DIGIFLAZZ_USERNAME,
    buyer_sku_code: env.DIGIFLAZZ_PLN_SKU,
    customer_no: customerNo,
    ref_id: refId,
    sign: md5(env.DIGIFLAZZ_USERNAME + env.DIGIFLAZZ_API_KEY + refId),
    testing: env.DIGIFLAZZ_TESTING
  };
  const response = await client.post<DigiflazzEnvelope>("/transaction", payload);
  return response.data;
}

export async function getDeposit() {
  const payload = {
    cmd: "deposit",
    username: env.DIGIFLAZZ_USERNAME,
    sign: md5(env.DIGIFLAZZ_USERNAME + env.DIGIFLAZZ_API_KEY + "depo")
  };
  const response = await client.post<DigiflazzEnvelope>("/cek-saldo", payload);
  return response.data;
}
