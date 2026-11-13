# Pet Adventure Chest

Daily-mission progress is counted only when a chest start is confirmed by the
existing `In Adventure` state check. Claim taps are not treated as confirmed
progress because the current claim flow has no reliable post-claim state
signal. Unknown starts and exhausted attempts do not report progress.
