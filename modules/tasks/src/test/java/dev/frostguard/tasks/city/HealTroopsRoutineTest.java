package dev.frostguard.tasks.city;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealTroopsRoutineTest {
    @BeforeAll static void nativeLibrary() throws Exception {
        OpenCvPatternLocator.loadNativeLibrary();
    }

    @Test void batchModeIsConservative() {
        assertTrue(HealTroopsRoutine.shouldQuickSelect(null));
        assertTrue(HealTroopsRoutine.shouldQuickSelect("Full - Quick Select"));
        assertFalse(HealTroopsRoutine.shouldQuickSelect("Small - Current selection"));
    }

    @Test void countsAndTimersHandleLiveFormats() {
        assertEquals(1131, HealTroopsRoutine.injuredCount("1,131/19,300"));
        assertEquals(0, HealTroopsRoutine.injuredCount("0 / 19,300"));
        assertNull(HealTroopsRoutine.injuredCount("unreadable"));
        assertEquals(643, HealTroopsRoutine.parseTime("00:10:43").toSeconds());
        assertEquals(153, HealTroopsRoutine.parseTime("00:02:33").toSeconds());
        assertNull(HealTroopsRoutine.parseTime("none"));
        assertNull(HealTroopsRoutine.parseTime("00:00:00"));
    }

    @Test void maskedLiveHealPanelRequiresTitleAndBlueHealButton() throws Exception {
        byte[] screenshot=resource("healing-page.jpg");
        assertTrue(matches(screenshot,TemplatesEnum.INFIRMARY_HEAL_TITLE_2026,80,8,302,75,86),
                "Original 2026 screen title must be recognizable");
        assertTrue(matches(screenshot,TemplatesEnum.INFIRMARY_HEAL_BUTTON_2026,510,1155,675,1235,85),
                "Only the blue Heal button should be recognized");
        assertFalse(matches(screenshot,TemplatesEnum.INFIRMARY_CITY_SELECTED_2026,265,505,460,585,85));
    }

    @Test void maskedLiveCitySelectionIsNotHealPage() throws Exception {
        byte[] city=resource("city-selected.jpg");
        assertTrue(matches(city,TemplatesEnum.INFIRMARY_ACTIVE_HEALING_2026,250,615,330,680,90));
        assertTrue(matches(city,TemplatesEnum.INFIRMARY_CITY_SELECTED_2026,270,505,460,585,84));
        assertTrue(matches(city,TemplatesEnum.INFIRMARY_CITY_HEAL_2026,420,760,595,920,84));
        assertFalse(matches(city,TemplatesEnum.INFIRMARY_HEAL_TITLE_2026,80,8,302,75,85));
        assertFalse(matches(city,TemplatesEnum.INFIRMARY_HEAL_BUTTON_2026,510,1155,675,1235,85));
    }

    @Test void actualIdleInfirmaryAllowsOnlySafeHealingAction() throws Exception {
        byte[] city=resource("idle-selected.jpg");
        assertTrue(matches(city,TemplatesEnum.INFIRMARY_CITY_SELECTED_2026,270,505,460,585,84));
        assertTrue(matches(city,TemplatesEnum.INFIRMARY_CITY_HEAL_2026,420,760,590,930,85));
        assertFalse(matches(city,TemplatesEnum.INFIRMARY_ACTIVE_HEALING_2026,250,615,330,680,90));
        assertFalse(matches(city,TemplatesEnum.INFIRMARY_HEAL_TITLE_2026,80,8,302,75,85));
    }

    @Test void afterRealHealingAllWoundedWereRecoveredAndNoHealButtonIsVisible() throws Exception {
        byte[] healed=resource("empty-page.jpg");
        assertTrue(matches(healed,TemplatesEnum.INFIRMARY_HEAL_TITLE_2026,80,8,302,75,85));
        assertTrue(matches(healed,TemplatesEnum.INFIRMARY_EMPTY_2026,100,565,625,650,85));
        assertFalse(matches(healed,TemplatesEnum.INFIRMARY_HEAL_BUTTON_2026,510,1155,675,1235,85));
    }

    private static boolean matches(byte[] frame,TemplatesEnum target,int left,int top,int right,int bottom,double threshold) {
        return OpenCvPatternLocator.locatePattern(frame,target.getTemplate(),
                new PointData(left,top),new PointData(right,bottom),threshold).isFound();
    }
    private static byte[] resource(String name) throws Exception {
        try(var in=Objects.requireNonNull(
                HealTroopsRoutineTest.class.getResourceAsStream("/infirmary/"+name))) {
            return in.readAllBytes();
        }
    }
}
