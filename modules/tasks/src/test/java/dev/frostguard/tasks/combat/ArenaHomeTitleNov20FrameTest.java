package dev.frostguard.tasks.combat;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.awt.image.BufferedImage;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArenaHomeTitleNov20FrameTest {
    @BeforeAll static void init() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }
    private RawImageData frame(String resource) throws Exception {
        BufferedImage im=ImageIO.read(Objects.requireNonNull(
                getClass().getResourceAsStream(resource)));
        byte[] rgba=new byte[im.getWidth()*im.getHeight()*4];
        int p=0;
        for(int y=0;y<im.getHeight();y++)for(int x=0;x<im.getWidth();x++) {
            int argb=im.getRGB(x,y);
            rgba[p++]=(byte)(argb>>>16);rgba[p++]=(byte)(argb>>>8);
            rgba[p++]=(byte)argb;rgba[p++]=(byte)255;
        }
        return RawImageData.capture(rgba,im.getWidth(),im.getHeight(),32);
    }
    @Test void exactNovember2026ArenaHomeScreenshotMustBeRecognized() throws Exception {
        assertTrue(ArenaLiveFrame.arenaHome(
                frame("/arena/live-20261120/arena-home-header-redacted.png")),
                "Sidebar navigation must accept this real Arena of Expedition header");
    }
    @Test void currentChallengeListHeaderIsRecognizedOnRealScreenshot() throws Exception {
        var list=frame("/arena/live-20261120/arena-list-header-redacted.png");
        assertTrue(ArenaLiveFrame.challengeList(list),
                "A real Challenge List must not be ignored after its Go transition");
        assertTrue(ArenaLiveFrame.listClose(list).isFound(),
                "The current Challenge List X must be positively recognized to recover Home");
        assertFalse(ArenaLiveFrame.arenaHome(list));
    }
    @Test void matchingArenaTitleDoesNotMeanChallengeListAlreadyOpen() throws Exception {
        var home=frame("/arena/live-20261120/arena-home-header-redacted.png");
        assertFalse(ArenaLiveFrame.challengeList(home));
    }
    @Test void heroOrIntelPagesAreNotArenaHome() throws Exception {
        assertFalse(ArenaLiveFrame.arenaHome(
                frame("/intel/journey-victory-20260821.png")));
    }
}
