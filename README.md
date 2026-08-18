# Smart Biller V4

Aplikasi monitoring operasional PLN Pascabayar, pembayaran Digiflazz, PDIL, mapping lokasi, invoice, pencarian pelanggan, dan leaderboard.

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

Copy `.env.example` to `.env` and configure PostgreSQL, JWT, CORS, and Digiflazz credentials. Never commit real API keys or production credentials.

For testing, set `DIGIFLAZZ_TESTING=true`.

## Production notes

1. Backend must run over HTTPS.
2. Never put Digiflazz API credentials in the APK/frontend.
3. Set `CORS_ORIGIN` to the frontend domain.
4. Configure Digiflazz webhook to `/api/webhooks/digiflazz`.
5. Late-payment penalty must come from the official billing source/API; the app does not invent the nominal amount.

## Android build

The GitHub Actions workflow builds the Capacitor Android project and publishes the debug APK as an artifact.
