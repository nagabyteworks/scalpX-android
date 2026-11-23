# scalpX Android API Contract

## Session authentication

Android sends:

```json
POST /api/auth/daily-login
{ "totp": "123456" }
```

Android stores only the returned JWT in Android Keystore-backed encrypted storage. The TOTP, broker credentials, broker access tokens, and target-management state never remain on Android.

No device ID is generated, transmitted, stored, or used for authentication.

## Protected REST requests

All `/api/*` requests except `/api/auth/daily-login` and broker OAuth callbacks use:

```text
Authorization: Bearer <JWT>
```

WebSocket handshake:

```text
Authorization: Bearer <JWT>
```

## REST endpoints

- `GET /health`
- `GET /api/app/config`
- `GET /api/auth/login-url`
- `GET /api/auth/status`
- `GET /api/market-data/sensex`
- `GET /api/option-chain`
- `POST /api/orders/one-click`
- `PATCH /api/ladders/{token}`
- `POST /api/orders/exit-position`
- `POST /api/orders/exit-all`
- `GET /api/trades/today`

Mutations attach a fresh UUID `commandId` and Android applies a local anti-double-tap cooldown.

## WebSocket

Android connects to `wss://<backend>/ws` with bearer authentication. It receives throttled trading snapshots and priority events including order fills, SL ratchets, rejection, stale feed, session updates, and manual-intervention requirements.

Android never performs trading arithmetic, broker API calls, or RAM-target reconstruction. It renders backend-authoritative prices and position status.
