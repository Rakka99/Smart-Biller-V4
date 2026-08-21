-- Add explicit Biller -> RBM A-E ownership and IAK transaction tracking.

CREATE TABLE "RBM" (
  "id" TEXT NOT NULL,
  "code" TEXT NOT NULL,
  "name" TEXT NOT NULL,
  "sequence" INTEGER NOT NULL,
  "billerId" TEXT NOT NULL,
  "ulpId" TEXT NOT NULL,
  "active" BOOLEAN NOT NULL DEFAULT true,
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP(3) NOT NULL,
  CONSTRAINT "RBM_pkey" PRIMARY KEY ("id")
);

ALTER TABLE "Customer" ADD COLUMN "rbmId" TEXT;
ALTER TABLE "Customer" ADD COLUMN "billerId" TEXT;
ALTER TABLE "Payment" ADD COLUMN "iakTrId" INTEGER;

CREATE UNIQUE INDEX "RBM_billerId_code_key" ON "RBM"("billerId", "code");
CREATE INDEX "RBM_ulpId_code_idx" ON "RBM"("ulpId", "code");
CREATE INDEX "Customer_billerId_rbmId_idx" ON "Customer"("billerId", "rbmId");
CREATE UNIQUE INDEX "Payment_iakTrId_key" ON "Payment"("iakTrId");

ALTER TABLE "RBM" ADD CONSTRAINT "RBM_billerId_fkey"
  FOREIGN KEY ("billerId") REFERENCES "User"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE "RBM" ADD CONSTRAINT "RBM_ulpId_fkey"
  FOREIGN KEY ("ulpId") REFERENCES "ULP"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE "Customer" ADD CONSTRAINT "Customer_rbmId_fkey"
  FOREIGN KEY ("rbmId") REFERENCES "RBM"("id") ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE "Customer" ADD CONSTRAINT "Customer_billerId_fkey"
  FOREIGN KEY ("billerId") REFERENCES "User"("id") ON DELETE SET NULL ON UPDATE CASCADE;
