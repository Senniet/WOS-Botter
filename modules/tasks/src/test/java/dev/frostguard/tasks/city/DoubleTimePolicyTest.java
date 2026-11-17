package dev.frostguard.tasks.city;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DoubleTimePolicyTest {
    @Test void sufficientPointsAndVerifiedExactOfferAreRequired() {
        assertTrue(DoubleTimePolicy.mayEnact(4_200_000L,800_000L,true,true));
        assertTrue(DoubleTimePolicy.mayEnact(800_000L,800_000L,true,true));
        assertFalse(DoubleTimePolicy.mayEnact(799_999L,800_000L,true,true));
        assertFalse(DoubleTimePolicy.mayEnact(null,800_000L,true,true));
        assertFalse(DoubleTimePolicy.mayEnact(2_000_000L,null,true,true));
        assertFalse(DoubleTimePolicy.mayEnact(2_000_000L,50_000L,true,true));
        assertFalse(DoubleTimePolicy.mayEnact(2_000_000L,800_000L,false,true));
        assertFalse(DoubleTimePolicy.mayEnact(2_000_000L,800_000L,true,false));
    }
}
