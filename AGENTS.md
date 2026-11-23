# Android Repository Instructions

- Android is a thin trading interface; zero broker APIs and zero trading math belong on the device.
- The app starts directly in the trading cockpit with no application TOTP or device login.
- Never generate, persist, transmit, or validate a device UUID.
- Never add device provisioning or revocation screens or calls.
- Store only the backend-issued bearer session in Android Keystore-backed secure storage.
- Never store broker credentials or broker session tokens.
- Never log broker credentials or broker session tokens.
- Use HTTPS and bearer authentication for REST and WebSocket traffic.
- Keep the UI server-authoritative for live prices, position state, SL, target, P&L, and management status.
