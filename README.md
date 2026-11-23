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

## Testing & Verification
- Unit tests cover ViewModel state flow, DTO serialization, OkHttp request headers, and error handling.
- Run tests via Gradle:
  ```bash
  ./gradlew test
  ```
