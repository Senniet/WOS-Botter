package dev.frostguard.tasks.pets;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PetAdventureLiveNov20FrameTest {
    private static final PointData START = new PointData(0,0);
    private static final PointData END = new PointData(720,1280);
    @BeforeAll static void setup() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }
    private byte[] frame(String name) throws IOException {
        try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/pets/live-20261120/"+name))) {
            return in.readAllBytes();
        }
    }
    @Test void liveStartedPurpleChestHasCountdownPillAndIdleChestsRemain() throws Exception {
        byte[] map=frame("map-active-with-idle.png");
        var timers=OpenCvPatternLocator.locateAllPatterns(map,TemplatesEnum.PETS_CHEST_ADVENTURE_TIMER,
                START,END,80,5).stream().filter(ImageSearchResultData::isFound).toList();
        var purple=OpenCvPatternLocator.locateAllPatterns(map,TemplatesEnum.PETS_CHEST_PURPLE,
                START,END,90,3).stream().filter(ImageSearchResultData::isFound).toList();
        System.out.println("PET-LIVE timers="+timers+" purple="+purple);
        assertFalse(timers.isEmpty(),"Current running chest MUST have a timer");
        // The live occupied chest changes into a golden badge, so the PURPLE template
        // finds only the OTHER idle chest. Its timer is still visible beneath the
        // original chest at approximately (96,421); position is the durable identity.
        assertTrue(PetAdventureDecisions.startedChestHasTimer(new PointData(96,421),timers),
                "The running chest is identified by its original position and timer");
        assertTrue(PetAdventureDecisions.occupiedByTimer(new PointData(96,421),timers),
                "Timer under the originally started chest proves it is already running");
        assertTrue(purple.stream().noneMatch(chest -> PetAdventureDecisions.occupiedByTimer(chest.getPoint(),timers)),
                "The purple icon should not match the newly transformed occupied badge");
        assertTrue(purple.stream().anyMatch(chest -> !PetAdventureDecisions.occupiedByTimer(chest.getPoint(),timers)),
                "At least one other purple chest should still be idle");
    }
    @Test void liveOccupiedOverlayShowsInAdventure() throws Exception {
        var hit=OpenCvPatternLocator.locatePattern(frame("in-adventure-purple.png"),
                TemplatesEnum.PETS_CHEST_IN_ADVENTURE,START,END,90);
        System.out.println("PET-LIVE overlay="+hit);
        assertTrue(hit.isFound());
    }
}
