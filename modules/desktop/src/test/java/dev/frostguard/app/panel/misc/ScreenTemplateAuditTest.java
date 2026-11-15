package dev.frostguard.app.panel.misc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

import static dev.frostguard.app.panel.misc.ScreenTemplateAudit.*;
import static org.junit.jupiter.api.Assertions.*;

class ScreenTemplateAuditTest {
    @TempDir Path tmp;

    @Test void requiresTwoConsistentFramesForVerification() {
        Match first = new Match(98, 600, 1180);
        Match same = new Match(97, 601, 1183);
        Match elsewhere = new Match(92, 32, 27);
        assertEquals(Verdict.NEEDS_TWO_FRAMES, classify(List.of(first), List.of(), 2, 40, 40));
        assertEquals(Verdict.POSITION_INCONSISTENT, classify(List.of(first, elsewhere), List.of(), 2, 40, 40));
        assertEquals(Verdict.CONSISTENT_NEEDS_NEGATIVES, classify(List.of(first, same), List.of(), 1, 40, 40));
        assertEquals(Verdict.VERIFIED_CONTEXT, classify(List.of(first, same),
                List.of(new Match(50, 1, 1), new Match(60, 1, 1)), 2, 40, 40));
    }

    @Test void rejectsCrossScreenHitsAndTinySymbols() {
        List<Match> positive = List.of(new Match(100, 100, 100), new Match(99, 101, 100));
        assertEquals(Verdict.CROSS_SCREEN_COLLISION,
                classify(positive, List.of(new Match(95, 20, 30)), 2, 40, 40));
        assertEquals(Verdict.SMALL_TEMPLATE_REVIEW,
                classify(positive, List.of(), 2, 11, 40));
        assertEquals(Verdict.NOT_CONFIRMED,
                classify(List.of(new Match(50, -1, -1), positive.get(0)), List.of(), 2, 40, 40));
    }

    @Test void labelsAndStoresScreensInOwnWorkspace() throws Exception {
        Path screenshot = tmp.resolve("example.png");
        ImageIO.write(new BufferedImage(72, 128, BufferedImage.TYPE_INT_RGB), "png", screenshot.toFile());
        Path stored = save(tmp, "home", screenshot);
        assertTrue(stored.startsWith(tmp.resolve("screens/home")));
        assertEquals(1, loadScreens(tmp).get("home").size());
        assertThrows(java.io.IOException.class, () -> save(tmp, "home", screenshot));
        assertEquals(1, loadScreens(tmp).get("home").size());
        assertThrows(IllegalArgumentException.class, () -> save(tmp, "../../private", screenshot));
        assertEquals("home", groupForTemplate(Path.of("/x/templates/home/newSurvivors.png")));
        assertEquals("world", groupForTemplate(Path.of("/x/templates/world.png")));
        assertEquals("unclassified", groupForTemplate(Path.of("/x/random/image.png")));
    }
}
