package dev.frostguard.tasks.alliance;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AllianceChestLiveFrameTest {
    private BufferedImage screen() {
        return new BufferedImage(720,1280,BufferedImage.TYPE_INT_RGB);
    }
    private BufferedImage withLiveCrop(String file,int x,int y) throws IOException {
        BufferedImage frame=screen();
        try(var stream=getClass().getResourceAsStream("/alliance/"+file)) {
            assertNotNull(stream,"Missing redacted live Serve-EMU crop "+file);
            BufferedImage crop=ImageIO.read(stream);
            Graphics2D g=frame.createGraphics();
            try {g.drawImage(crop,x,y,null);} finally {g.dispose();}
        }
        return frame;
    }

    @Test void detectsRealCurrentGiftClaimButtonOnLiveFrame() throws IOException {
        var visible=withLiveCrop("gift-claim-20261117-before.png",500,705);
        assertTrue(AllianceChestLiveFrame.findGiftClaim(visible).isFound());
    }

    @Test void refusesDisabledLootClaimAllFromRealAfterClaimScreen() throws IOException {
        var disabled=withLiveCrop("loot-claim-all-disabled-20261117.png",210,1150);
        assertFalse(AllianceChestLiveFrame.findLootClaimAll(disabled).isFound());
    }

    @Test void requiresGreenWithWhiteLabelInsideActualLootFooter() {
        var frame=screen();
        Graphics2D g=frame.createGraphics();
        try {
            g.setColor(new Color(40,184,65));
            g.fillRoundRect(220,1169,279,78,22,22);
            g.setColor(Color.WHITE);
            g.setFont(new java.awt.Font("SansSerif",java.awt.Font.BOLD,24));
            g.drawString("Claim All",293,1215);
        } finally {g.dispose();}
        assertTrue(AllianceChestLiveFrame.findLootClaimAll(frame).isFound());
    }

    @Test void progressNeedsActualNumericRegionChangeNotJustAnUnrelatedAnimation() {
        var before=screen();
        var after=screen();
        Graphics2D g=after.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(275,293,50,18);
        } finally {g.dispose();}
        assertTrue(AllianceChestLiveFrame.keyProgressChanged(before,after));
        assertFalse(AllianceChestLiveFrame.keyProgressChanged(before,before));
        assertFalse(AllianceChestLiveFrame.keyProgressChanged(before,null));
    }

    @Test void missingOffsetCannotCrashOrCreateZeroDelayLoop() {
        assertEquals(60,AllianceChestRoutine.safeChestInterval(null));
        assertEquals(60,AllianceChestRoutine.safeChestInterval(0));
        assertEquals(60,AllianceChestRoutine.safeChestInterval(-1));
        assertEquals(20,AllianceChestRoutine.safeChestInterval(20));
    }
}
