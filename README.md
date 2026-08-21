# Smart Biller V4

Aplikasi monitoring operasional PLN Pascabayar, pembayaran IAK, PDIL, mapping lokasi, invoice, pencarian pelanggan, dan leaderboard.

## Business rules billing

- **PREVENTIF**: tagihan bulan berjalan, tanggal 1–20.
- **KOREKTIF**: tagihan bulan berjalan, tanggal 21–akhir bulan; sistem memberi flag keterlambatan. Nominal denda mengikuti sumber billing/API resmi.
- **IRISAN**: tagihan dari bulan sebelumnya yang masih belum lunas ketika sudah masuk bulan berikutnya.
- Leaderboard utama menghitung **pelanggan unik**; jumlah periode/tagihan disimpan sebagai statistik pendukung.

## Roles

- **ADMIN**: akses penuh, master wilayah/ULP/user, PDIL, monitoring, laporan.
- **SUPERVISOR**: monitoring wilayah/ULP, PDIL, leaderboard, pencarian.
- **BILLER**: pencarian, inquiry, pembayaran, invoice, transaksi.

## Project structure

- `apps/api` — backend Node.js/Express + Prisma/PostgreSQL.
- `apps/web` — frontend Vite/React + Capacitor Android.
- `prisma` — database schema.
- `data` — manifest/import configuration. Customer master production/demo data is intentionally excluded from this public repository.
- `.github/workflows` — GitHub Actions Android build.

## IAK integration

Smart Biller menggunakan **IAK sebagai satu-satunya provider PLN Pascabayar**.

Flow transaksi:

1. Biller mengirim IDPEL ke `/api/pln/inquiry`.
2. Backend membuat `ref_id` unik dan melakukan inquiry `PLNPOSTPAID` ke IAK.
3. `tr_id` dari hasil inquiry disimpan di response inquiry dan digunakan untuk payment.
4. Backend memanggil `pay-pasca` menggunakan `tr_id`.
5. Jika hasil payment pending/response tidak diterima, backend menggunakan `checkstatus` berdasarkan `ref_id` sebelum menentukan hasil akhir.
6. Setelah sukses, billing ditandai PAID dan invoice dibuat.

IAK API key dan username hanya boleh berada di backend environment; jangan pernah memasukkannya ke APK, frontend, atau repository.

## Local setup

```bash
npm install
cd apps/api && npm install
cd ../web && npm install
npx cap add android
npm run build
npx cap sync android
```

## Environment

Copy `.env.example` to `.env` dan konfigurasi PostgreSQL, JWT, CORS, serta credential IAK. Jangan commit API key atau credential production.

Untuk development IAK, gunakan endpoint sandbox yang tercantum pada dokumentasi IAK dan `IAK_PLN_PRODUCT_CODE=PLNPOSTPAID`.

## Production notes

1. Backend harus berjalan melalui HTTPS.
2. Jangan pernah menaruh IAK API credentials di APK/frontend.
3. Set `CORS_ORIGIN` ke domain frontend.
4. Pastikan IP server production sudah diizinkan pada pengaturan API IAK jika diperlukan.
5. Untuk transaksi `PENDING`, lakukan `checkstatus` sebelum retry payment agar tidak terjadi pembayaran ganda.
6. Denda keterlambatan harus berasal dari sumber billing/API resmi; aplikasi tidak mengarang nominal denda.

## Android build

The GitHub Actions workflow builds the Capacitor Android project and publishes the debug APK as an artifact.
