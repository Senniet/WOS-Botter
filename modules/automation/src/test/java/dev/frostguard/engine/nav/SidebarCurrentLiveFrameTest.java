package dev.frostguard.engine.nav;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SidebarCurrentLiveFrameTest {
    @Test void recognizesModernDailyAndCityTabsFromWosSennie121() throws Exception {
        for (var entry : java.util.Map.of(
                "daily", SidebarSection.DAILY,
                "city", SidebarSection.CITY).entrySet()) {
            BufferedImage frame = ImageIO.read(Objects.requireNonNull(
                    getClass().getResource("/serveemu/sidebar-modern-" + entry.getKey() + "-redacted.png")));
            assertEquals(entry.getValue(), SidebarFrameClassifier.selectedSection(frame).orElse(null), entry.getKey());
            assertTrue(SidebarFrameClassifier.isCurrentLayout(frame),
                    "Modern tab tap geometry required for " + entry.getKey());
        }
    }
}
