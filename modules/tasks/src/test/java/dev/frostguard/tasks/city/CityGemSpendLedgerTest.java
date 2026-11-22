package dev.frostguard.tasks.city;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CityGemSpendLedgerTest {
    @TempDir Path dir;
    private static final LocalDate DATE = LocalDate.of(2026, 11, 20);

    @Test void purchasesReserveBeforeTapPersistAndCannotExceedDailyLimit() throws Exception {
        assertEquals(250,CityGemSpendLedger.remaining(dir,1L,DATE,250));
        assertTrue(CityGemSpendLedger.reserve(dir,1L,DATE,250,100));
        assertTrue(CityGemSpendLedger.reserve(dir,1L,DATE,250,100));
        assertFalse(CityGemSpendLedger.reserve(dir,1L,DATE,250,100));
        assertEquals(50,CityGemSpendLedger.remaining(dir,1L,DATE,250));
        assertTrue(CityGemSpendLedger.reserve(dir,1L,DATE,250,50));
        assertEquals(0,CityGemSpendLedger.remaining(dir,1L,DATE,250));
        assertEquals(250,Integer.parseInt(Files.readString(dir.resolve("profile-1-2026-11-20.txt")).trim()));
        assertEquals(250,CityGemSpendLedger.remaining(dir,2L,DATE,250));
        assertEquals(250,CityGemSpendLedger.remaining(dir,1L,DATE.plusDays(1),250));
    }

    @Test void zeroCapBrokenJournalAndNegativeAmountsAreDenied() throws Exception {
        assertFalse(CityGemSpendLedger.reserve(dir,1L,DATE,0,100));
        assertFalse(CityGemSpendLedger.reserve(dir,1L,DATE,200,-2));
        assertFalse(CityGemSpendLedger.reserve(dir,1L,DATE,100,101));
        Files.writeString(dir.resolve("profile-1-2026-11-20.txt"),"broken");
        assertThrows(java.io.IOException.class,
                () -> CityGemSpendLedger.reserve(dir,1L,DATE,100,50));
    }
}
