# scalpX Android Application

## Project Purpose & Architecture
**scalpX Android** is a server-authoritative, ultra-low-latency options scalping cockpit built with Modern Android Architecture (Jetpack Compose, Kotlin Coroutines & Flow, MVI pattern).

### Core Architectural Invariants
1. **Server-Authoritative Thin Client:** Zero business logic, risk calculations, or ratcheting SL mathematics run on the phone. The phone strictly renders state snapshots received via WebSocket/REST and dispatches user commands with unique UUID idempotency tokens.
2. **Zero Broker Secrets on Phone:** Broker credentials (`api_key`, `api_secret`, Flattrade passwords, session tokens) NEVER touch the Android client.
3. **TOTP-only Authentication:** The app displays a six-digit TOTP field and sends only the code to the backend.

---

## Authentication Flow & Error Handling

```
[ Android App ]                                     [ scalpX Backend ]
      |                                                     |
      |-- 1. POST /api/auth/daily-login ------------------>|
      |      Body: { "totp": "123456" }                    |
      |<-- 2. HTTP 200 OK with JWT (or Error Code) ---------|
      |                                                     |
      |-- 3. Stores JWT in Keystore Encrypted Storage       |
      |-- 4. Dispatches /api/app/config & connects /ws      |
```

### Error Code Mappings

| Backend Error Code / HTTP Status | Displayed User Message |
|---|---|
| `401` / `INVALID_TOTP` | Invalid TOTP code. Try again. |
| `401` / `TOTP_REPLAY` | This TOTP code has already been used. Wait for the next code. |
| `503` / `TOTP_NOT_CONFIGURED` | Authentication is temporarily unavailable. Contact the administrator. |
| Network / Connection Failure | Unable to connect to the server. Check your connection. |

---

## Testing & Verification
- Unit tests cover ViewModel state flow, DTO serialization, OkHttp request headers, and error handling.
- Run tests via Gradle:
  ```bash
  ./gradlew test
  ```
