# Android Development and Production Environments

scalpX Android is a server-authoritative client for the scalpX backend. The
backend remains the only component that holds Flattrade credentials, performs
broker OAuth, resolves instruments, and submits authorized order commands.
The app's `SERVER` build setting chooses the backend origin used by both
Retrofit and the WebSocket client; it never falls back between environments.

## Start the backend on macOS

From `scalpX-backend`:

```bash
cp -n .env.example .env
```

Install Node.js 20 or newer before running the backend commands below.
Keep `.env` local and untracked. Populate the backend-only Flattrade account
fields and generate `DB_SESSION_ENCRYPTION_KEY` using the command in
`.env.example`. Leave `TRADING_ENABLED=false`. The local backend cannot
authenticate to Flattrade until valid backend-side credentials and a
broker-supported callback configuration are supplied; these values never
belong in Android.

Install dependencies, compile, and run using the scripts defined by the
backend's `package.json`:

```bash
npm install
npm run build
npm start
```

The default listener is `127.0.0.1:3000`. Check the health endpoint on the Mac:

```bash
curl http://127.0.0.1:3000/health
```

## Select SERVER

Debug builds default to `SERVER=dev` and `DEV_SERVER_URL=http://10.0.2.2:3000`.
`10.0.2.2` is the Android Emulator's special route to the host computer. Run:

```bash
cd scalpX-android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

To explicitly choose either server for a debug build:

```bash
./gradlew assembleDebug -PSERVER=dev
./gradlew assembleDebug -PSERVER=prod
```

For a physical phone, configure the MacBook's current Wi-Fi/LAN address; do
not use the example address as a fixed value:

```bash
./gradlew assembleDebug -PSERVER=dev -PDEV_SERVER_URL=http://<mac-lan-ip>:3000
```

For example, find the active Wi-Fi address in macOS System Settings under
Network, then substitute that address. The backend must bind to the LAN
interface for phone access; set `HOST=0.0.0.0` in the backend's untracked
`.env`. Keep the Mac and phone on the same trusted Wi-Fi, allow incoming
connections for the local Node process in the macOS firewall, and ensure
wireless client isolation is off. Do not configure router port forwarding or
expose port 3000 to the public internet. Return `HOST` to `127.0.0.1` when
physical-device access is no longer needed.

Production builds are pinned to `SERVER=prod`, with `DEV_SERVER_URL` omitted
from their generated configuration:

```bash
cd scalpX-android
./gradlew assembleRelease
```

The production backend is `https://trade.nagabyte.com`; its WebSocket uses
`wss://`. Release network security disallows cleartext HTTP. Debug builds alone
allow HTTP for development.

## Startup and connection checks

In debug builds, Android logs the selected environment, REST base URL, and
WebSocket URL under the `ScalperApplication` tag:

```bash
adb logcat -s ScalperApplication
```

The cockpit first calls the selected backend's `/health` endpoint. The header
shows the selected environment and API reachability; connection errors appear
in the message strip. Temporary failures retry with exponential delay capped at
30 seconds. The WebSocket and market-data requests start only after health
responds successfully. A development failure stays in DEV and is never sent
to production as a fallback.

The local `/health` response proves only that the backend process is reachable;
it does not indicate a valid Flattrade session, live market feed, or permission
to place orders.
