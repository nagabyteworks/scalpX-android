# scalpX Android Implementation Review

## Project purpose

scalpX Android is a server-authoritative MVI cockpit for a single-user options scalper. It renders backend broker/feed/trading state and sends user intents. It does not call Flattrade directly or make independent trading decisions.

## Changes in this review

- Auth status DTOs distinguish broker session validity, socket transport, broker socket authentication, and fresh market data.
- Automatic ATM inference from the Android spot display was removed. The app uses ATM only when the backend supplies it and the matching strike has both CE and PE tokens.
- Changing the selected index clears the old spot, expiry, and option contracts. Failed chain requests clear stale contracts as well.
- Initial spot-change state is neutral instead of fabricated.

## Data limitation

There is no validated SENSEX index token/instrument master in the backend, and no verified SENSEX spot tick currently flows from Flattrade through the backend to Android. The backend now returns an unavailable response for LIVE option-chain requests until authoritative master data and a fresh underlying quote are configured. The Android app consequently has no ATM or contracts to submit based on that missing data.

## Verification

`./gradlew testDebugUnitTest assembleDebug` passed, including Android unit tests and debug APK assembly.
