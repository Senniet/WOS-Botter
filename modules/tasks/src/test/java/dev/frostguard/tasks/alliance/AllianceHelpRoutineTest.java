package dev.frostguard.tasks.alliance;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class AllianceHelpRoutineTest {
    @BeforeAll static void cv()throws Exception{OpenCvPatternLocator.loadNativeLibrary();}

    @Test void isStandaloneTaskWithStableExistingAllianceToggle() {
        assertEquals(41, TpDailyTaskEnum.ALLIANCE_HELP_REQUEST.routineCode());
        assertEquals("Alliance Help Request",TpDailyTaskEnum.ALLIANCE_HELP_REQUEST.displayText());
        assertEquals(ConfigurationKeyEnum.ALLIANCE_HELP_BOOL,
                     TpDailyTaskEnum.ALLIANCE_HELP_REQUEST.activationSwitch());
        long uniqueIds=java.util.Arrays.stream(TpDailyTaskEnum.values())
                .mapToInt(TpDailyTaskEnum::routineCode).distinct().count();
        assertEquals(TpDailyTaskEnum.values().length,uniqueIds);
    }

    @Test void twoStableObservationsAndIndependentCompletionAreMandatory() {
        var present=ImageSearchResultData.hit(350,350,99.9);
        assertTrue(AllianceHelpVerification.sameVisibleButton(present,
                ImageSearchResultData.hit(359,360,96)));
        assertFalse(AllianceHelpVerification.sameVisibleButton(present,
                ImageSearchResultData.hit(410,350,97)));
        assertFalse(AllianceHelpVerification.sameVisibleButton(present,ImageSearchResultData.miss()));
        assertFalse(AllianceHelpVerification.sameVisibleButton(ImageSearchResultData.miss(),present));
        assertTrue(AllianceHelpVerification.verifiedClaim(true,true,false));
        assertFalse(AllianceHelpVerification.verifiedClaim(true,true,true),
                "A still-visible help shortcut cannot be reported as completed");
        assertFalse(AllianceHelpVerification.verifiedClaim(true,false,false),
                "The game must still be on City after the tap");
        assertFalse(AllianceHelpVerification.verifiedClaim(false,true,false),
                "An already absent request is not proof a Help tap happened");
    }

    @Test void noHelpTapOnCityWithoutRequestOrOnKnownNonCityScreens()throws Exception{
        var city=frame("/initialize/home-city-anchor-redacted.png");
        assertTrue(AllianceHelpRoutine.isCity(city));
        assertFalse(AllianceHelpRoutine.locateHelp(city).isFound(),
                    "City with no visible help request must not be clicked");
        var squad=frame("/initialize/squad-settings-page-redacted.png");
        assertFalse(AllianceHelpRoutine.isCity(squad));
        assertFalse(AllianceHelpRoutine.locateHelp(squad).isFound());
        var world=frame("/initialize/home-world-anchor-redacted.png");
        assertFalse(AllianceHelpRoutine.isCity(world));
        assertFalse(AllianceHelpRoutine.locateHelp(world).isFound());
        assertFalse(AllianceHelpRoutine.locateHelp(
                RawImageData.capture(new byte[4],1,1,32)).isFound());
    }

    private static RawImageData frame(String name)throws Exception {
        BufferedImage image;
        try(var source=Objects.requireNonNull(AllianceHelpRoutineTest.class.getResourceAsStream(name))){
            image=Objects.requireNonNull(ImageIO.read(source));
        }
        byte[] data=new byte[image.getWidth()*image.getHeight()*4];
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){
            int color=image.getRGB(x,y),i=(y*image.getWidth()+x)*4;
            data[i]=(byte)(color>>16);data[i+1]=(byte)(color>>8);data[i+2]=(byte)color;data[i+3]=(byte)255;
        }
        return RawImageData.capture(data,image.getWidth(),image.getHeight(),32);
    }
}
