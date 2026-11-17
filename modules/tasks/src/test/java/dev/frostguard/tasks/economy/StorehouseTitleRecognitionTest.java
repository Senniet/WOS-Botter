package dev.frostguard.tasks.economy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StorehouseTitleRecognitionTest {
    @Test void acceptsActualStorehouseNameDespiteOcrSpacing() {
        assertTrue(StorehouseChestRoutine.isStorehouseTitle("30  Storehouse"));
        assertTrue(StorehouseChestRoutine.isStorehouseTitle("S t o r e h o u s e"));
        assertTrue(StorehouseChestRoutine.isStorehouseTitle("Storehouse\n"));
    }

    @Test void refusesOtherBuildingsAndPartialNames() {
        assertFalse(StorehouseChestRoutine.isStorehouseTitle("Research Center"));
        assertFalse(StorehouseChestRoutine.isStorehouseTitle("Store"));
        assertFalse(StorehouseChestRoutine.isStorehouseTitle("Details"));
        assertFalse(StorehouseChestRoutine.isStorehouseTitle(null));
    }
}
