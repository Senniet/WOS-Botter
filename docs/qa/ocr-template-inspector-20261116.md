# OCR, template library, run viewer and code flow — 16/17 November 2026

## Live dashboard navigation

1. Open authenticated `https://10.0.0.100:9097` → **Templates**.
2. **OCR-status en fouten** distinguishes empty extracted text, rejected text, ADB screenshot/capture errors and other OCR errors based on recent uploaded diagnostic log tails. Counts **may repeat** because several uploads contain overlapping log excerpts. This is not an absolute OCR pass-rate.
3. **Taakflow en Task Coach-runviewer** → choose `Training` or any of the 62 tasks. Actual `execute()` method calls (source file and line references) and conditional steps are shown; Training also has an operator-friendly, source-grounded full logical flow. Choosing a run shows every logged template, whether it hit, method (color/mono/multiscale), max score, chronological observations and stored full-screen captures. Click a recognition row to view the corresponding screenshot if that frame was actually saved; positive coordinates are overlaid as a green dot. A missing matching capture is clearly labelled, not synthesized.
4. **Registered template library** → filter by category, name, source uses and observed hit/misses; click a template to see its real PNG, dimensions, source dependencies and Coach evidence. Buttons: **Verberg in mijn lijst**, **Markeer voor opschonen**, **Herstel**. State survives restart in private `~/.local/share/survivalops-qa/template-review.json` and is reversible. **No production template, source file, runtime lookup or Git repository content is changed by these buttons.** Protected guards cannot be marked for retirement. Enable **Toon verborgen templates** to restore hidden entries.
5. **Extra PNG-bestanden die niet in het template-register staan** contains all images listed as extra by the pre-existing template audit (28 observed in current report). Even extra previews use an explicit inventory allowlist.

## OCR finding

- `ResilientOcrExecutor` reports `FAILED` when it received no readable text; with `transportFailure=none`, it generally means empty OCR output, *not* a crashed OCR engine. `REJECTED` means there was text but the requested acceptor predicate rejected it. Some real `RuntimeException` messages show `ADB screencap failed`, so the problem can occur during image acquisition **before OCR**.
- Bundled `tools/tesseract/eng.traineddata` (plus chi_sim and osd) exists in the source and in the desktop packaging directory. Actual Tesseract (not mocked) recognized synthesized clear `TRAIN 160` text in a new `TesseractRealTextSmokeTest`. Full modules/api + modules/vision Maven reactor tests pass when Surefire JVM uses a non-full disk-backed temporary directory (earlier default `/tmp` failures were `Disk quota exceeded`, unrelated to Tesseract).
- **Windows MSI runtime OCR remains untested in this environment**; source/build test pass does not prove every on-device OCR crop or ADB screenshot transport is correct. Future troubleshooting should retain cropped image, coordinates, raw OCR string, and transport error, with privacy scrubbing.

## Safety and acceptance

The run viewer is restricted to authentic uploaded Task Coach screenshot filenames and existing run IDs. Source-preview images are limited to the audited registered or explicitly listed extra asset paths. All endpoints require existing QA authentication; review actions require same-origin POST. Requests never execute arbitrary shell commands or write into the bot's template directory. Hiding/removal-review choices are **QA-only**, not an actual deletion capability. Full in-game template retirement requires proof of safe replacement and targeted regression tests.

A high confidence score only demonstrates a visual match. An in-game task is complete only with separately verified postconditions and operator/reviewer QA signoffs.
