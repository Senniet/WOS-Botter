package dev.frostguard.tasks.dailies;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChiefOrderSpendPolicyTest {
    private static ChiefOrderLiveFrame.Inspection offer(
            ChiefOrderLiveFrame.Action a, long cost, String name) {
        return new ChiefOrderLiveFrame.Inspection(a,cost,name,"Enact",
                String.valueOf(cost));
    }

    @Test void authorizedChiefOrderPointsAreSpendableWhenBalanceIsVerifiedTwice() {
        var p=offer(ChiefOrderLiveFrame.Action.COSTS_POINTS,50000,"Productivity Day");
        assertTrue(ChiefOrderSpendPolicy.allowed(p,p,"Productivity Day",
                4_200_000L,4_200_000L));
        assertTrue(ChiefOrderSpendPolicy.allowed(p,p,"Productivity Day",
                50_000L,50_000L));
    }

    @Test void insufficientBalanceOrUnreadableAmountsNeverSpend() {
        var p=offer(ChiefOrderLiveFrame.Action.COSTS_POINTS,50000,"Productivity Day");
        assertFalse(ChiefOrderSpendPolicy.allowed(p,p,"Productivity Day",49000L,49000L));
        assertFalse(ChiefOrderSpendPolicy.allowed(p,p,"Productivity Day",null,4200000L));
        assertFalse(ChiefOrderSpendPolicy.allowed(p,p,"Productivity Day",4200000L,null));
        assertFalse(ChiefOrderSpendPolicy.allowed(p,p,"Productivity Day",4200000L,9000L));
    }

    @Test void wrongOrderUnstableCostOrUnrecognizedPriceNeverSpend() {
        var p=offer(ChiefOrderLiveFrame.Action.COSTS_POINTS,50000,"Productivity Day");
        var wrong=offer(ChiefOrderLiveFrame.Action.COSTS_POINTS,50000,"Urgent Mobilization");
        var priceChange=offer(ChiefOrderLiveFrame.Action.COSTS_POINTS,10000,"Productivity Day");
        var unknown=offer(ChiefOrderLiveFrame.Action.UNVERIFIED,-1,"Productivity Day");
        assertFalse(ChiefOrderSpendPolicy.allowed(p,wrong,"Productivity Day",4200000L,4200000L));
        assertFalse(ChiefOrderSpendPolicy.allowed(p,priceChange,"Productivity Day",4200000L,4200000L));
        assertFalse(ChiefOrderSpendPolicy.allowed(unknown,unknown,"Productivity Day",4200000L,4200000L));
        assertFalse(ChiefOrderSpendPolicy.allowed(p,p,"Urgent Mobilization",4200000L,4200000L));
    }

    @Test void pointsHaveNoArtificialCapWhenTheVerifiedOrderBalanceCoversTheCost() {
        for (String name : List.of("Rush Job", "Urgent Mobilization", "Productivity Day")) {
            var expensive=offer(ChiefOrderLiveFrame.Action.COSTS_POINTS,800000,name);
            assertTrue(ChiefOrderSpendPolicy.allowed(expensive,expensive,name,4200000L,4200000L));
            assertFalse(ChiefOrderSpendPolicy.allowed(expensive,expensive,name,799999L,799999L));
        }
    }

    @Test void missingOrUnreliableOrderStatusesCanOnlyOpenPreview() {
        assertTrue(ChiefOrderRoutine.eligibleForIconSelection(
                ChiefOrderRoutine.OrderGate.UNKNOWN,ChiefOrderRoutine.OrderGate.UNKNOWN));
        assertTrue(ChiefOrderRoutine.eligibleForIconSelection(
                ChiefOrderRoutine.OrderGate.TRY_ICON,ChiefOrderRoutine.OrderGate.UNKNOWN));
        assertFalse(ChiefOrderRoutine.eligibleForIconSelection(
                ChiefOrderRoutine.OrderGate.ALREADY_USED,ChiefOrderRoutine.OrderGate.UNKNOWN));
        assertFalse(ChiefOrderRoutine.eligibleForIconSelection(
                ChiefOrderRoutine.OrderGate.TRY_ICON,ChiefOrderRoutine.OrderGate.ALREADY_USED));
    }

    @Test void actualProductivityOfferIsRecognizedAsVerifiablePointCost() throws Exception {
        var screen=ChiefOrderLiveFrameTest.frame("productivity-cost.png");
        var inspection=ChiefOrderLiveFrame.inspect(screen,"Productivity Day");
        assertEquals(ChiefOrderLiveFrame.Action.COSTS_POINTS,inspection.action());
        assertEquals(50000L,inspection.cost());
        assertTrue(ChiefOrderSpendPolicy.allowed(inspection,inspection,"Productivity Day",
                4_200_000L,4_200_000L));
        var balance=ChiefOrderLiveFrame.availableOrderPoints(
                ChiefOrderLiveFrameTest.frame("order-list-balance.jpg"));
        System.out.println("Saved 4.2M Chief Order frame OCR balance="+balance);
    }
}
