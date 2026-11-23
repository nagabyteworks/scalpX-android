# Android Trading Dashboard Layout Restoration

## Project purpose

scalpX Android is the thin user interface for a server-authoritative LIVE index-options trading system. This layout restoration preserves existing Flattrade integration and trading components while ensuring the complete dashboard is usable across phone screen sizes.

## Root cause

The dashboard collapse was caused by layout and inset changes that no longer supplied the dashboard body as a normal bounded Compose column:

- edge-to-edge drawing and toolbar insets were being handled inconsistently;
- the broker toolbar did not receive a complete safe-area-aware top layout;
- the dashboard body relied on a constrained/non-scrollable area and could lose all available content height;
- the recent surface structure could place the toolbar without preserving the expected top-to-bottom dashboard flow.

The recent header change did not remove any trading components. The layout correction removes the nested Scaffold measurement, uses an explicit fixed-height toolbar, and gives the scrollable body the remaining weighted height.

## Implementation

`MainActivity` enables AndroidX edge-to-edge rendering and keeps the full-screen Compose root.

`ScalperCockpitScreen` uses a direct full-screen `Column` with:

- `fillMaxSize()` on the root;
- `WindowInsets.safeDrawing` for system UI content;
- a fixed 44 dp broker header at the top;
- `weight(1f)` on the full-width scrollable dashboard body;
- bounded minimum position-content height instead of a fixed screen height.

`Zone1BrokerHeader` retains the existing broker name, LIVE badge, latency, Connect Broker action, theme control, and settings control.

## Invariants

- The broker toolbar is first and remains immediately below the safe top area.
- Main dashboard content is never vertically centred.
- Smaller screens scroll vertically.
- Navigation bars, status bars, and camera cutouts are excluded from interactive content.
- Existing market-data, Flattrade OAuth, order, SL, target, and position components remain unchanged.
- No mock market data or paper-trading behavior is added.

## Verification

Run:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Expected verification:

- unit tests compile and pass;
- the debug APK builds;
- the broker toolbar renders at the top;
- configuration, spot, options, tabs, and position content are in normal document order;
- shorter screens scroll to all controls;
- system bars and cutouts do not cover header or body content.
