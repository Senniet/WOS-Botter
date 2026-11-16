# DO_EXPLORATION: optional Quick Exploration, receipt, and chest

**Operator report:** Task Coach `20261116-115615-1eyvuzs`, Monday 16 Nov 2026,
started 11:56:27 UTC. The scheduler reported `SUCCEEDED` while its 17
captured frames showed **Quick Exploration in progress…** with **Skip**.
The code considered confirming the quick dialog to be the *end* of the task;
it never waited for the completed result, dismissed its receipt or checked
the additional chest. Existing general Rewards templates scored low on these
frames because neither the in-progress animation nor the distinct completion
receipt is an ordinary Rewards popup.

## Live evidence on Serve-EMU

- Coach frame `014-shared_rewards_banner_current-miss.jpg` displayed a moving
  quick battle, labelled **Quick Exploration in progress…** and **Skip**.
  Frame `017-serve_emu_rewards_banner-miss.jpg` shows the same labels later in
  the animation: both current images are matched by the new dedicated checks.
- The live emulator exposed a separate screen headed **Quick Exploration
  completed**, followed by **Total Rewards**, with footer **Tap any blank
  space to exit**. One click in the blank footer area (355,1155) returned to
  the Exploration page, proving that the result receipt is a separate page.
- On that page a green chest **Claim** was available. Clicking it opened the
  current **Idle Income** modal with another green **Claim**, then a blue
  **Rewards** popup with footer **Tap anywhere to exit**. This second popup
  is visually different: existing shared banner templates scored only ~39%
  on the masked live screenshot. A dedicated pair of header + footer templates
  now recognizes this popup. The in-game operation involved only free claim
  actions; no gems/keys/purchases.
- A subsequent unrelated War page was observed after a manual click. That
  is *not* accepted as verified Exploration or City Home in the new code:
  inconsistent navigation must mark the task incomplete, not succeeded.

## Implemented flow

1. At task start, inspect for a pre-existing *specific* Quick progress or
   completion receipt from an interrupted previous run. Resume these before
   any Explore/Fight action, never click an unrelated screen.
2. On Exploration, verify the Explore button and tap it. Detect the distinct
   orange Cancel / blue Confirm **Quick Exploration** dialog with existing
   text and two-frame validation. If it is not present, retain the original
   Squad Settings → optional Quick Deploy → Fight → Victory/Defeat route.
3. If Quick is present and confirmed, wait for its progress. Optionally click
   the **Skip** control, but only after *two* real frames match both the
   Quick progress title and Skip CTA; only one skip is allowed. Never tap a
   generic blue button, modal or other panel.
4. Require the distinct **Quick Exploration completed** title *and* its
   **Tap any blank space to exit** footer in two fresh frames. Tap once,
   require that receipt disappears and that the known Exploration page
   reappears. A confirmed Quick-dialog click is NOT a verified result.
5. If an additional green chest Claim is visible on the verified Exploration
   page, require two-frame green/white button recognition before tapping.
   Verify **Idle Income** title, then its green inner Claim twice. Confirm
   the distinct new Rewards sheet by header + footer twice, close once and
   verify return to Exploration. If no extra green chest is visible, skip
   this optional section without inventing a claim.
6. Return to City using the known Exploration page title and verified header
   Back arrow on two frames, then independently verify Home. Unknown pages,
   missing rewards/claims, changing buttons or a failed Home check set
   `markWorkIncomplete` and a conservative ten-minute retry; never silently
   report `SUCCEEDED` for an unresolved quick result.
7. Task Coach now logs new template scores with the real search region and
   screenshots, so independent operator/reviewer signoff has actionable proof.

## Test evidence and limits

- Replay tests include both moving quick-progress frames, the actual completed
  screen, negative Squad Settings and Chief Rewards frames, safe Skip/Exit
  transitions, unsuccessful/changed screens, the new Idle Income Rewards
  variant, and the existing 2026 Idle Income title and claim geometry.
- Full Java tasks reactor: **767 tests, zero failures/errors, 4 skipped**;
  desktop Maven package built successfully.
- Positive live test confirmed the Quick-completion closing tap and the
  separate Idle Income claim / Rewards transitions, but the entire new
  automated **Windows** sequence is not yet proven end-to-end. Do not mark
  `DO_EXPLORATION` or `EXPLORATION_CHEST` as jointly verified until a fresh
  Task Coach run records these postconditions.
