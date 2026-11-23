# scalpX Android Application

## Project Purpose & Architecture
**scalpX Android** is a server-authoritative, ultra-low-latency options scalping cockpit built with Modern Android Architecture (Jetpack Compose, Kotlin Coroutines & Flow, MVI pattern).

### Core Architectural Invariants
1. **Server-Authoritative Thin Client:** Zero business logic, risk calculations, or ratcheting SL mathematics run on the phone. The phone strictly renders state snapshots received via WebSocket/REST and dispatches user commands with unique UUID idempotency tokens.
2. **Zero Broker Secrets on Phone:** Broker credentials (`api_key`, `api_secret`, Flattrade passwords, session tokens) NEVER touch the Android client.
3. **Direct Startup:** The app opens the trading cockpit without application TOTP or device authentication.

---

## Startup and broker OAuth

Android opens the trading cockpit directly. It does not require TOTP or an application session.

Flattrade authentication is a separate browser flow:

1. Android calls `GET /api/auth/login-url`.
2. The official Flattrade login opens in Chrome Custom Tabs.
3. The backend handles the callback and broker session.
4. Android receives only backend status and live market/position updates.

---

## Trading dashboard layout

The main dashboard is implemented with a direct Compose `Column` in `ScalperCockpitScreen`.

- The broker toolbar is the pinned `topBar` and remains directly below system UI.
- `WindowInsets.safeDrawing` is applied to the root so status, navigation, and cutout regions are accounted for.
- The broker header is fixed at 44 dp and remains the first child.
- The complete dashboard body uses a bounded, full-width/full-height column and vertical scrolling.
- The positions, order-book, and trade-book section has a bounded content height inside the scrollable dashboard.
- No dashboard section is removed or vertically centred.

The layout is intentionally not a fixed-height screen: smaller devices scroll while larger devices expose more of the positions content. No mock market data or fake trading state is introduced by layout changes.

---

## Live market-data lifecycle

The Android client has no broker credentials and does not synthesize market
prices. On cockpit startup it opens `/ws`, checks backend/broker status, and
requests the selected index spot. If the broker has not delivered the first
fresh tick yet, spot retrieval retries while the backend reports a transient
waiting, stale, disconnected, or reconnecting state. A fresh spot automatically
triggers the option-chain request; the backend returns verified expiries,
contracts and lot sizes and selects the nearest available ATM CE/PE pair.

Contract metadata is resolved only on the backend from Flattrade's current
NFO/BFO index-derivatives Scrip Master. The app receives the actual listed
symbols, tokens, expiries, strikes and lot sizes; it never creates option
symbols or tokens. Prices come only from fresh authenticated Flattrade
WebSocket ticks, not from the contract master or local estimates.

The backend sends an initial WebSocket snapshot and coalesced updates containing
position state plus the selected market data. Android applies each market
snapshot to its MVI state, including the spot and per-contract timestamps, then
renders current prices only when their ticks are fresh. Feed status and
authentication, token-resolution, or subscription failures are surfaced as
status/error text, including the backend's structured error code. WebSocket
snapshot parse failures are logged in debug builds without logging frame
contents. Changing index or expiry cancels obsolete requests and requests an
updated chain; all
verified contracts for that expiry are subscribed, so selecting another ATM
strike uses its already-streamed CE/PE ticks. After WebSocket reconnect, the
backend re-subscribes to broker instruments and the app resumes from current
snapshots.

## Direct order controls and safety

The four option actions dispatch their intended Buy or Sell action directly;
there is no One Click Enabled setting or confirmation dialog. Sell exits an
existing long position for that contract, or submits a short entry when no
position exists. The client preserves tap cooldown and in-flight action locks,
and blocks new entries unless the broker is connected, trading is enabled,
account state is certain, and the selected option tick is fresh. Backend
contract validation, risk checks, command de-duplication, protective stops and
the `TRADING_ENABLED=false` kill switch remain authoritative.

The UI is an order-entry interface. Do not treat successful compilation or
fixture-based tests as evidence of live Flattrade connectivity; a fresh
authorized broker session and live market session are required to verify that.

---

## Testing & Verification
- Unit tests cover ViewModel state flow, DTO serialization, OkHttp request headers, and error handling.
- Run tests via Gradle:
  ```bash
  ./gradlew test
  ```
