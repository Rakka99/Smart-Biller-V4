# Smart Biller — Native Android

Smart Biller V4 includes a native Android module built with Kotlin + Jetpack Compose + Material 3. The visual system uses a responsive glassmorphism style with PLN-inspired blue/yellow accents.

## Native stack
- AGP built-in Kotlin support (AGP 9.2.0)
- Kotlin runtime aligned with AGP built-in Kotlin
- Compose compiler Gradle plugin 2.2.10
- Gradle 9.4.1
- Jetpack Compose BOM 2026.06.01
- Material 3
- MVVM-style ViewModel + Coroutines
- DataStore session
- Retrofit + OkHttp
- Room runtime reserved for the production offline cache phase

## Build
The GitHub Actions workflow uses JDK 17, Gradle 9.4.1, Android API 37, and Build Tools 36.0.0, matching AGP 9.2 requirements.

## Database
The Android app keeps the existing customer database. Customer records are read through the Supabase review API from the `smart_biller_review_customers` table; the Excel file is not bundled into the APK.

## Review login
- admin / change-me-now
- supervisor / change-me-now
- biller / change-me-now

## Review payment
Payments are simulated only. No live PLN or provider transaction is executed from the review API.

The legacy React/Capacitor web client remains in `apps/web` while the native module becomes the primary Android client.
