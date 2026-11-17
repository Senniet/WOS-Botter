package dev.frostguard.engine.schedule;

import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.runtime.WorkspacePaths;
import dev.frostguard.api.runtime.WorkspaceSession;
import dev.frostguard.engine.service.ProfileService;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskQueueLifecycleRecoveryTest {

    @BeforeAll
    static void initializeWorkspace() {
        WorkspaceSession.initializeLayout(WorkspacePaths.current());
    }

    @Test
    void schedulerCrashStopsVisibleRunningStateAndReleasesEmulatorLease() throws Exception {
        var account = profile("fatal");
        var queue = new FatalQueue(account);
        queue.start();
        assertTrue(queue.entered.await(3,TimeUnit.SECONDS));
        waitUntilInactive(queue);
        assertFalse(queue.isActive(),"a dead scheduler must not stay Running");
        assertFalse(queue.hasLiveExecutor());
        assertEquals(1, queue.leaseReleases.get());
        assertTrue(queue.awaitStop(Duration.ofSeconds(1)).terminated());
    }

    @Test
    void ordinaryStopReleasesLeaseOnlyAfterItsWorkerHasTerminated() throws Exception {
        var account = profile("stop");
        var queue = new IdleQueue(account);
        queue.start();
        assertTrue(queue.entered.await(3,TimeUnit.SECONDS));
        queue.requestStop();
        assertTrue(queue.awaitStop(Duration.ofSeconds(3)).terminated());
        assertFalse(queue.hasLiveExecutor());
        assertEquals(1,queue.leaseReleases.get());
    }

    private static AccountDescriptor profile(String suffix) {
        AccountDescriptor account = new AccountDescriptor(null,
                "workflow " + suffix + " " + UUID.randomUUID(), "0", false, 100L, 30L);
        assertTrue(ProfileService.obtain().createAccount(account));
        return account;
    }

    private static void waitUntilInactive(TaskQueue q) throws InterruptedException {
        long end = System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
        while (q.hasLiveExecutor() && System.nanoTime()<end) Thread.sleep(10);
    }

    private static final class FatalQueue extends TaskQueue {
        final CountDownLatch entered=new CountDownLatch(1);
        final AtomicInteger leaseReleases=new AtomicInteger();
        FatalQueue(AccountDescriptor account){super(account);}
        @Override protected void acquireSlot() {markSlotAcquired();}
        @Override void runSchedulerTick() {
            entered.countDown();
            throw new IllegalStateException("simulated queue-level failure");
        }
        @Override protected void releaseEmulatorSlotLease() {leaseReleases.incrementAndGet();}
    }

    private static final class IdleQueue extends TaskQueue {
        final CountDownLatch entered=new CountDownLatch(1);
        final AtomicInteger leaseReleases=new AtomicInteger();
        IdleQueue(AccountDescriptor account){super(account);}
        @Override protected void acquireSlot() {markSlotAcquired();}
        @Override void runSchedulerTick() {
            entered.countDown();
            try {Thread.sleep(Duration.ofMinutes(2));}
            catch (InterruptedException interrupted) {Thread.currentThread().interrupt();}
        }
        @Override protected void releaseEmulatorSlotLease() {leaseReleases.incrementAndGet();}
    }
}
