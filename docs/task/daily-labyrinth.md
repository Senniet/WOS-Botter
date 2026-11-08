# Daily Labyrinth

The routine's recoverable-error handler must not swallow scheduler/device
control signals. Preemption, cancellation, reconnect/cooldown, and ADB failures
are rethrown through `TaskControlSignals` so the scheduler can yield or stop
instead of replacing the control action with the routine's one-hour retry.
