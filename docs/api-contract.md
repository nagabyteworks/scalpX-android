# scalpX Android API Contract

## Direct startup

Android opens the trading cockpit without TOTP, device provisioning, device UUIDs, or an application bearer session.
Broker credentials and broker access tokens remain backend-only.

## Protected REST requests

Android sends ordinary HTTP REST requests and opens `/ws` without application-device authentication headers.

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
