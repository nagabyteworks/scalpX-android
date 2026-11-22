# scalpX Android Application Documentation

## Project Purpose & Architecture
**scalpX Android** is a server-authoritative, ultra-low-latency options scalping cockpit built with Modern Android Architecture (Jetpack Compose, Kotlin Coroutines & Flow, MVI pattern).

### Core Architectural Invariants
1. **Server-Authoritative Thin Client:** Zero business logic, risk calculations, or ratcheting SL mathematics run on the phone. The phone strictly renders state snapshots received via WebSocket/REST and dispatches user commands with unique UUID idempotency tokens.
2. **Zero Broker Secrets on Phone:** Broker credentials (`api_key`, `api_secret`, Flattrade passwords, session tokens) NEVER touch the Android client.
3. **Hardware-Tied Device Identity:** The app generates a permanent UUID upon first install, stored securely in Android Keystore-backed `EncryptedSharedPreferences`.
4. **Minimalistic Authentication Screen:** The user only inputs the 6-digit TOTP code. The app automatically attaches the persistent `X-Device-Id` header and `deviceId` payload.

---

## Device UUID Lifecycle
1. **Generation:** When `SecureConfigStore` initializes, it checks `KEY_DEVICE_ID`. If empty, it creates `UUID.randomUUID().toString()` and stores it in encrypted shared preferences.
2. **Persistence:** Survives application restarts, background kills, and standard app updates.
3. **Usage:** Attached automatically via `AuthInterceptor` on all HTTP/WebSocket requests (`X-Device-Id: <UUID>`) and in the JSON body of `authenticateDevice`.

---

## Authentication Flow & Error Handling

```
[ Android App ]                                     [ scalpX Backend ]
      |                                                     |
      |-- 1. POST /api/auth/device/authenticate ----------->| (Validates X-Device-Id & TOTP)
      |      Header: X-Device-Id: <UUID>                    |
      |      Body: { deviceId, totp }                       |
      |<-- 2. HTTP 200 OK with JWT (or Error Code) ---------|
      |                                                     |
      |-- 3. Stores JWT in Keystore Encrypted Storage       |
      |-- 4. Dispatches /api/app/config & connects /ws      |
```

### Error Code Mappings

| Backend Error Code / HTTP Status | Displayed User Message |
|---|---|
| `403` / `DEVICE_NOT_AUTHORIZED` | This device has not been registered. Contact the administrator to authorize it. |
| `401` / `DEVICE_REVOKED` | This device has been revoked. Contact the administrator. |
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
