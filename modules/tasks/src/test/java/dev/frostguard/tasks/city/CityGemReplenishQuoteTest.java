package dev.frostguard.tasks.city;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CityGemReplenishQuoteTest {
    private static java.util.Optional<CityGemReplenishQuote.Quote> quote(
            String ratio, String product, String gives, String label, String price,
            String gems, int limit) {
        return CityGemReplenishQuote.parse(ratio, product, gives, label, price, gems, limit);
    }
    @Test void exactLiveIronShortageNeedsOnlyOnePack() {
        var q=quote("14,597/21,000", "10K Iron (Secured)",
                "Gives 10,000 Iron (Secured).", "Buy & Use", "100", "453", 100).orElseThrow();
        assertEquals(6403,q.deficit());
        assertEquals(1,q.packsNeeded());
        assertEquals(100,q.totalGems());
        assertEquals(10_000,q.packSize());
    }
    @Test void requirementAbovePackRoundsUpButOnlyIfBudgetCoversAllPacks() {
        assertTrue(quote("0/20,001", "10K Iron (Secured)", "Gives 10,000 Iron (Secured).",
                "Buy & Use", "100", "453", 300).isPresent());
        assertFalse(quote("0/20,001", "10K Iron (Secured)", "Gives 10,000 Iron (Secured).",
                "Buy & Use", "100", "453", 200).isPresent());
    }
    @Test void unverifiedCostResourcesAndWalletFailClosed() {
        assertTrue(quote("14,597/21,000", "10K Wood (Secured)",
                "Gives 10,000 Iron (Secured).", "Buy & Use", "100", "453", 100).isEmpty());
        assertTrue(quote("14,597/21,000", "10K Iron (Secured)",
                "Gives 10,000 Iron (Secured).", "VIP Shop", "100", "453", 100).isEmpty());
        assertTrue(quote("14,597/21,000", "10K Iron (Secured)",
                "Gives 20,000 Iron (Secured).", "Buy & Use", "100", "453", 100).isEmpty());
        assertTrue(quote("14,597/21,000", "10K Iron (Secured)",
                "Gives 10,000 Iron (Secured).", "Buy & Use", "100", "50", 100).isEmpty());
        assertTrue(quote("14,597/21,000", "10K Iron (Secured)",
                "Gives 10,000 Iron (Secured).", "Buy & Use", "100", "453", 0).isEmpty());
        assertTrue(quote("21,000/21,000", "10K Iron (Secured)",
                "Gives 10,000 Iron (Secured).", "Buy & Use", "100", "453", 100).isEmpty());
        assertTrue(quote("??/21,000", "10K Iron (Secured)",
                "Gives 10,000 Iron (Secured).", "Buy & Use", "100", "453", 100).isEmpty());
    }
}
