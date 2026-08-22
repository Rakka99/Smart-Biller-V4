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

## Supabase authentication and database

BMAX/Smart Biller menggunakan **Supabase sebagai identity dan database access layer**.

- Supabase Auth menangani login email/password dan session.
- `public.profiles` menyimpan role dan scope operasional (`ADMIN`, `SUPERVISOR`, `BILLER`, region, ULP, RBM).
- Row Level Security (RLS) membatasi data sesuai role dan wilayah/ULP/Biller.
- `bmax_rbms` menyimpan kepemilikan RBM A-E per Biller.
- `customers.bmax_biller_id` dan `customers.bmax_rbm_id` mengikat pelanggan ke pemilik operasional.
- Android menggunakan Supabase publishable key saja; **service-role key tidak boleh berada di APK**.
- IAK credentials tetap server-side dan tidak pernah dikirim ke Android.

Supabase project digunakan pada region Asia Pacific (Singapore) dan endpoint client dikonfigurasi melalui `SUPABASE_URL` serta `SUPABASE_PUBLISHABLE_KEY`.

## Biller and RBM structure

Setiap Biller memiliki tepat lima kode rute RBM:

- RBM A
- RBM B
- RBM C
- RBM D
- RBM E

Database mengikat setiap RBM ke **Biller + ULP**, kemudian setiap pelanggan dapat diikat ke Biller dan RBM. Inquiry dan pembayaran juga harus memeriksa scope kepemilikan sebelum transaksi diproses.

## Project structure

- `apps/api` — backend Node.js/Express + Prisma/PostgreSQL untuk business logic dan IAK.
- `apps/web` — frontend Vite/React + Capacitor Android.
- `android-app` — Android Kotlin/Jetpack Compose.
- `prisma` — database schema and migrations untuk backend.
- `data` — manifest/import configuration. Customer master production/demo data is intentionally excluded from this public repository.
- `.github/workflows` — GitHub Actions Android build.

## IAK integration

Smart Biller menggunakan **IAK sebagai satu-satunya provider PLN Pascabayar**.

Flow transaksi:

1. Biller mengirim IDPEL ke `/api/pln/inquiry`.
2. Backend membuat `ref_id` unik dan melakukan inquiry `PLNPOSTPAID` ke IAK.
3. `tr_id` dari hasil inquiry disimpan pada transaksi dan digunakan untuk payment.
4. Backend memanggil `pay-pasca` menggunakan `tr_id`.
5. Jika hasil payment pending/response tidak diterima, backend menggunakan `checkstatus` berdasarkan `ref_id` sebelum menentukan hasil akhir.
6. Setelah sukses, billing ditandai PAID dan invoice dibuat.

## Google Sheets temporary layer

Google Sheets + Google Apps Script dapat digunakan sebagai data source sementara untuk development/demo melalui repository abstraction. Data yang sudah disinkronkan tetap dicache di Room pada Android.

Mode:

- `GOOGLE_SHEETS` — temporary/development.
- `BACKEND_API` — production.

Perpindahan ke production tidak boleh mengubah Compose UI, ViewModel, UseCase, atau domain model.

## Local setup

```bash
npm install
cd apps/api && npm install
cd ../web && npm install
npx cap add android
npm run build
npx cap sync android
```

## Android Supabase configuration

Set environment berikut sebelum build Android:

```text
SUPABASE_URL=https://vgnynrzhanfnbifjedga.supabase.co
SUPABASE_PUBLISHABLE_KEY=<publishable-key>
```

Untuk GitHub Actions gunakan repository secrets:

```text
SUPABASE_URL
SUPABASE_PUBLISHABLE_KEY
MAPS_API_KEY
```

Publishable key boleh digunakan oleh client, tetapi akses data tetap harus dilindungi dengan RLS. Service-role key dan IAK API key tidak boleh ada di APK atau frontend.

## Production notes

1. Backend dan endpoint payment harus berjalan melalui HTTPS.
2. Supabase RLS wajib aktif untuk seluruh tabel yang diakses client.
3. Android hanya memakai Supabase publishable key.
4. IAK API credentials hanya berada pada backend/server environment.
5. Untuk transaksi `PENDING`, lakukan `checkstatus` sebelum retry payment agar tidak terjadi pembayaran ganda.
6. Denda keterlambatan harus berasal dari sumber billing/API resmi; aplikasi tidak mengarang nominal denda.
7. Aktifkan leaked password protection pada Supabase Auth sebelum production.

## Android build

The GitHub Actions workflow builds the Android project and publishes the debug APK as an artifact.
