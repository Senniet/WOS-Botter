package dev.frostguard.engine.schedule;

import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExclusiveUiTaskTest {
    @Test void transactionGuardsScreenAndAlwaysReleasesOnFailure() {
        var task = new TestTask();
        assertFalse(task.isExclusiveUiTransaction());
        assertTrue(task.injectable());
        assertThrows(IllegalStateException.class, () -> task.exclusive(() -> {
            assertTrue(task.isExclusiveUiTransaction());
            assertFalse(task.injectable());
            throw new IllegalStateException("UI changed");
        }));
        assertFalse(task.isExclusiveUiTransaction());
        assertTrue(task.injectable());
    }

    private static class TestTask extends DelayedTask {
        TestTask() { super(new AccountDescriptor(908121L,"test","0",false,100L,30L), TpDailyTaskEnum.INITIALIZE); }
        @Override protected void execute() {}
        void exclusive(Runnable runnable) { runExclusiveUiTransaction(runnable); }
        boolean injectable() { return acceptsInjections(); }
    }
}
