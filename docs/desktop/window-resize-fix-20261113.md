# SurvivalOps — resizing the undecorated Windows window (2026-11-13)

## Problem

The main JavaFX stage is `StageStyle.UNDECORATED` so Windows does not draw its normal resize borders. The optional JNA native window subclass adds `WS_THICKFRAME`, but its `WM_NCHITTEST` handler previously relied on the original Windows window procedure to report `HTLEFT/HTRIGHT/HTBOTTOM*`. With `WM_NCCALCSIZE` returning zero, that procedure can report only `HTCLIENT`, leaving the native resize border inert. The always-installed JavaFX fallback had an extremely narrow 6px edge, used bubble-phase mouse handlers (which child controls may consume), and relied on an earlier MouseMoved event to choose the drag direction.

## Changes

- Keep the normal resizable stage contract explicit in `FXApp` (`stage.setResizable(true)`) while preserving the app's custom title bar.
- The native Win32 callback now explicitly detects **all 4 edges and 4 corners** inside the undecorated client bounds and returns the corresponding standard `WM_NCHITTEST` result (`HTLEFT` through `HTBOTTOMRIGHT`). The resize border scales with `GetDpiForWindow` and has a conservative fallback margin on older Windows. The native hit testing does not take over when maximized, outside the stage or in ordinary client/title-bar buttons.
- The fallback JavaFX listener now uses a **12px edge margin**, captures press/drag/release in the event filter phase even when child content consumes mouse events, calculates the edge from the actual primary-button press instead of a stale hover event, and preserves the resize direction during dragging.
- Existing custom maximize/minimize, drag-to-snap, minimum window size (680 × 460), saved normal position/size and responsive center-content anchors remain unchanged.

## Validation

- Headless JUnit regression tests cover native Win32 edge/corner results, negative desktop coordinates, DPI margins, maximized/outside-window behavior, JavaFX 8-direction cursor detection, invalid coordinates and the installed event-filter path.
- Existing launcher layout and saved-window-bounds tests pass.
- Full Maven package preflight with the upcoming version succeeds.
- **Not yet confirmed on a physical Windows desktop:** actual interactive mouse-drag behavior; this requires installing the MSI and testing a real Windows graphical session. CI geometry tests and the Windows installer build cannot by themselves prove interactive drag.
