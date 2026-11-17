# Gather deployment progress

A gather tap is not sufficient evidence of deployment. Daily-mission progress
is reported only when a slot that was IDLE or RETURNING before the fill pass is
observed as an active GATHER march (OUTBOUND or WORKING) afterward. If one or
more dispatches cannot be confirmed, the routine sends no repeat tap in that
run and schedules a bounded March Queue recheck.

If a deployment tap was dispatched but no new march is confirmed, the routine
sets a five-minute March Queue retry and propagates an unknown-outcome failure
when no deployment was confirmed. Partial confirmed deployments are retained
as progress while the short recheck remains scheduled.
