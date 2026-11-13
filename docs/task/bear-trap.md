# Bear Trap

The next event-window schedule is written only after the current execution
finishes without an exception. Failures are propagated to `TaskQueue` so its
existing bounded retry and incident handling remain active; preemption is
propagated unchanged so the queue can yield to the higher-priority task.
Cleanup still runs for every outcome. A cleanup failure is suppressed onto an
existing execution failure rather than replacing the original cause.

The scheduling decision has unit-test coverage. Live event-window and rally
cleanup behavior still require account-log confirmation.
