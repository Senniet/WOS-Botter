# Daily Labyrinth

The routine's recoverable-error handler must not swallow scheduler/device
control signals. Preemption, cancellation, reconnect/cooldown, and ADB failures
are rethrown through `TaskControlSignals` so the scheduler can yield or stop
instead of replacing the control action with the routine's one-hour retry.


Daily-mission progress is reported only when one of the challenge paths was
actually entered. A run that finds no available challenge does not report
progress. The challenge result itself still needs a dedicated post-battle
visual confirmation signal.
