# Smart Biller — Native Android

Smart Biller V4 now includes a native Android module built with Kotlin + Jetpack Compose + Material 3. The visual system uses a responsive glassmorphism style with PLN-inspired blue/yellow accents.

## Native stack
- Kotlin 2.3.10
- Android Gradle Plugin 9.2.0
- Gradle 9.4.1
- Jetpack Compose BOM 2026.06.01
- Material 3
- MVVM-style ViewModel + Coroutines
- DataStore session
- Retrofit + OkHttp
- Room runtime reserved for the production offline cache phase

## Database
The Android app keeps the existing customer database. Customer records are read through the Supabase review API from the `smart_biller_review_customers` table; the Excel file is not bundled into the APK.

## Review login
- admin / change-me-now
- supervisor / change-me-now
- biller / change-me-now

## Review payment
Payments are simulated only. No live PLN or provider transaction is executed from the review API.

The legacy React/Capacitor web client remains in `apps/web` while the native module becomes the primary Android client.
