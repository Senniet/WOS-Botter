package dev.frostguard.tasks.alliance;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AllianceDonationBatchPolicyTest {
    @Test void batchesKeepFinalFreeAttemptForIndividualVerification() {
        assertEquals(4, AllianceDonationBatchPolicy.nextBatchSize(25,true));
        assertEquals(4, AllianceDonationBatchPolicy.nextBatchSize(6,true));
        assertEquals(4, AllianceDonationBatchPolicy.nextBatchSize(5,true));
        assertEquals(3, AllianceDonationBatchPolicy.nextBatchSize(4,true));
        assertEquals(2, AllianceDonationBatchPolicy.nextBatchSize(3,true));
        assertEquals(1, AllianceDonationBatchPolicy.nextBatchSize(2,true));
        assertEquals(1, AllianceDonationBatchPolicy.nextBatchSize(1,true));
        assertEquals(0, AllianceDonationBatchPolicy.nextBatchSize(0,true));
        assertEquals(0, AllianceDonationBatchPolicy.nextBatchSize(26,true));
    }

    @Test void droppedClicksAreCountedOnlyWhenVisualCounterConfirmsThem() {
        assertEquals(4, AllianceDonationBatchPolicy.confirmedDonationCount(25,21,4));
        assertEquals(2, AllianceDonationBatchPolicy.confirmedDonationCount(25,23,4));
        assertEquals(1, AllianceDonationBatchPolicy.confirmedDonationCount(1,0,1));
        assertEquals(-1, AllianceDonationBatchPolicy.confirmedDonationCount(25,25,4));
        assertEquals(-1, AllianceDonationBatchPolicy.confirmedDonationCount(25,20,4));
        assertEquals(-1, AllianceDonationBatchPolicy.confirmedDonationCount(25,26,4));
        assertEquals(-1, AllianceDonationBatchPolicy.confirmedDonationCount(25,-1,4));
        assertEquals(-1, AllianceDonationBatchPolicy.confirmedDonationCount(25,21,5));
    }

    @Test void neverQueuesAFreeTapAfterRemainingCountIsZero() {
        int available=25, emitted=0;
        while(available>0){
            int taps=AllianceDonationBatchPolicy.nextBatchSize(available,true);
            assertTrue(taps>0 && taps<=available);
            if(taps>1)assertTrue(available-taps>=1);
            available-=taps;
            emitted+=taps;
        }
        assertEquals(25,emitted);
        assertEquals(0,AllianceDonationBatchPolicy.nextBatchSize(available,true));
    }
}
