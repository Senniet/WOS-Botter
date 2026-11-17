# Gather recall

A recall tap is only a dispatched action. A march counts as recalled only when
the matching March Queue row transitions to RETURNING or IDLE. An unreadable or
unchanged row is an unknown outcome; the current batch stops without repeating
the tap. The last-recall timestamp is persisted only when at least one recall
is confirmed. Already-returning and unknown-phase marches are excluded from
future high-priority recall candidates.

The autojoin-off fallback also compares the March Queue before and after its
recall taps; it reports only observed slot transitions, not the number of taps
sent. During the return wait, empty or incomplete queue reads are treated as
unknown rather than proof that every troop is home.
