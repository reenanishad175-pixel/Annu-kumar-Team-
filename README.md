# Annu Kumar Team

Android app package `com.annukumar.team` (Kotlin, Java 17, compileSdk 35).

## Current build scope
- Firebase Phone Authentication with SMS OTP.
- Embedded BDG WIN WebView (default URL: https://bdgwin68.com/#/login).
- Best-effort visible-page reader for timer and period labels only; it is not a verified result feed.
- Manual entry of up to the latest 10 BIG/SMALL outcomes for a transparent paper-mode pattern summary.
- Demo session tracker: level increases after a marked LOSS; after a marked WIN, level resets to 1; 5 total marked WINs ends the demo session; 8 consecutive marked LOSSes cancels it.
- Telegram Bot Token and Chat ID settings with a test-message function. The token is stored in app-private SharedPreferences and is not encrypted.
- No wager submission, auto-clicking, auto-betting, or real-money transactions.

## Pattern analysis limitations
The analyzer only summarizes manually entered historical outcomes. It checks the latest three for alternating or repeated-side patterns, then uses the count across up to ten entries as a fallback. If the counts are close, it returns BET SKIP. These are descriptive heuristics, not statistically validated predictions; past outcomes do not establish the next result. Users must verify entries themselves. The app does not yet extract authoritative result history from BDG WIN or automatically verify WIN/LOSS against a period.

## Telegram
1. Create a bot with Telegram's official @BotFather.
2. Add the bot to your channel/group with permission to post.
3. Enter the bot token and destination chat ID in the app, save, and send a test message.
4. Keep the token private. Do not paste it into chats or commit it to source control.

Telegram messages are sent only for a user-triggered paper analysis or manually recorded demo result. Four daily timed sessions, five-minute READY messages, automatic referral promotions, and background scheduling are not implemented in this build.

## Firebase setup
1. Firebase Console → Authentication → Sign-in method → enable Phone.
2. Add the Android app SHA-1 and SHA-256 signing certificate fingerprints.
3. Configure allowed SMS regions and test phone numbers.
4. Keep `google-services.json` in `app/`; never commit service-account credentials.

## Build
GitHub Actions workflow: `.github/workflows/android-build.yml`. Pushes to `feature/full-android-app` trigger a debug APK build and upload an artifact when successful.
