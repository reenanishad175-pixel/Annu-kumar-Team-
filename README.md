# Annu Kumar Team

Android app package `com.annukumar.team` (Kotlin, Java 17, compileSdk 35).

## Included
- Firebase Phone Authentication with SMS OTP.
- Embedded BDG WIN WebView, default URL: https://bdgwin68.com/#/login.
- Best-effort visible page text reader for possible timer and period labels.
- Three local paper-prediction strategies.
- Period-change paper log and configurable paper stake.
- No wager submission, automated website taps, or real-money transactions. The app cannot verify whether the BDG account is Demo or Real.

## Firebase setup
1. Firebase Console → Authentication → Sign-in method → enable Phone.
2. Add the Android app SHA-1 and SHA-256 signing certificate fingerprints.
3. Configure allowed SMS regions and test phone numbers.
4. Keep `google-services.json` in `app/`; never commit service-account credentials.

Phone authentication uses OTP verification; it does not fabricate an email/password identity from a phone number.

## Build
Run `./gradlew assembleDebug` with Android SDK platform 35 and Gradle 8.9.
GitHub Actions: `.github/workflows/android-build.yml`.

## Limitations
BDG WIN is a third-party website. Its DOM, timer and period labels can change or may not be exposed as readable text. Detection is best-effort. Strategy outputs are demonstration heuristics, not statistically validated signals or guaranteed predictions.
