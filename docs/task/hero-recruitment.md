# Hero recruitment

A claim counts as daily-mission progress only when the claim button was detected,
tapped, and no longer appears in the same recruitment panel. If the button
remains visible, the result is unknown: the routine sends no second tap in that
run and schedules the existing five-minute fallback retry. No available claim
does not count as progress.
