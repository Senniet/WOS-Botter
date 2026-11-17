package dev.frostguard.engine.emulator;

import dev.frostguard.api.domain.AccountDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.PriorityQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** A cancelled profile must not leave a dead ticket at the head of the slot queue. */
class EmulatorControllerQueuedCancellationTest {
    @Test void interruptedWaitingProfileLeavesNoQueuedTicket() throws Exception {
        Constructor<EmulatorController> ctor = EmulatorController.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        EmulatorController controller = ctor.newInstance();
        Field maxSlots = EmulatorController.class.getDeclaredField("maxSlots");
        maxSlots.setAccessible(true);
        maxSlots.setInt(controller, 1);
        Field queued = EmulatorController.class.getDeclaredField("queue");
        queued.setAccessible(true);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean waitingInterrupted = new AtomicBoolean(false);
        Thread owner = new Thread(() -> {
            try {
                controller.adquireEmulatorSlot(profile(1,"0"), (thread, position) -> {});
                held.countDown();
                release.await();
            } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        });
        Thread waiter = new Thread(() -> {
            try {
                controller.adquireEmulatorSlot(profile(2,"1"), (thread, position) -> {});
            } catch (InterruptedException expected) { waitingInterrupted.set(true); }
        });
        try {
            owner.start();
            assertTrue(held.await(3, TimeUnit.SECONDS));
            waiter.start();
            boolean enqueued = false;
            for (int tries=0; tries<60; tries++) {
                if (((PriorityQueue<?>)queued.get(controller)).size() == 1) {
                    enqueued = true; break;
                }
                Thread.sleep(20);
            }
            assertTrue(enqueued, "second profile must have queued behind the first");
            waiter.interrupt();
            waiter.join(3000);
            assertFalse(waiter.isAlive());
            assertTrue(waitingInterrupted.get());
            assertTrue(((PriorityQueue<?>)queued.get(controller)).isEmpty(),
                    "interrupted task must leave no queue ticket behind");
        } finally {
            release.countDown();
            owner.join(3000);
            controller.resetSlots();
        }
    }
    private static AccountDescriptor profile(long id,String slot) {
        return new AccountDescriptor(id, "test-"+id, slot, true, 100L, 30L);
    }
}
