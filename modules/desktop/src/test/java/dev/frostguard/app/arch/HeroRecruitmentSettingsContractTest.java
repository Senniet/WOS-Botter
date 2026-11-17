package dev.frostguard.app.arch;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeroRecruitmentSettingsContractTest {
    @Test void persistedAdvancedEpicAndPointsTogglesAppearInCityEventsPage() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("modules/desktop"))) root=root.getParent();
        assertNotNull(root);
        String fxml=Files.readString(root.resolve("modules/desktop/src/main/resources/layout/CityEventsExtraLayout.fxml"));
        String controller=Files.readString(root.resolve(
                "modules/desktop/src/main/java/dev/frostguard/app/panel/city/CityEventsExtraLayoutController.java"));
        var controls=Map.of(
                "checkBoxHeroAdvancedFree", ConfigurationKeyEnum.HERO_RECRUIT_ADVANCED_FREE_BOOL,
                "checkBoxHeroEpicFree", ConfigurationKeyEnum.HERO_RECRUIT_EPIC_FREE_BOOL,
                "checkBoxHeroAdvancedKeys", ConfigurationKeyEnum.HERO_RECRUIT_ADVANCED_KEYS_BOOL,
                "checkBoxHeroEpicKeys", ConfigurationKeyEnum.HERO_RECRUIT_EPIC_KEYS_BOOL,
                "checkBoxHeroPointsChest", ConfigurationKeyEnum.HERO_RECRUIT_POINTS_CHEST_BOOL);
        for (var entry : controls.entrySet()) {
            assertTrue(fxml.contains("fx:id=\""+entry.getKey()+"\""),entry.getKey()+" missing in FXML");
            assertTrue(controller.contains("new ToggleBinding("+entry.getKey()+", ConfigurationKeyEnum."
                    +entry.getValue().name()+")"),entry.getKey()+" not persisted");
            assertEquals(ConfigurationKeyEnum.ConfigCategory.SHOPS,entry.getValue().category());
        }
        assertEquals("true",ConfigurationKeyEnum.HERO_RECRUIT_ADVANCED_FREE_BOOL.getDefaultValue());
        assertEquals("true",ConfigurationKeyEnum.HERO_RECRUIT_EPIC_FREE_BOOL.getDefaultValue());
        assertEquals("true",ConfigurationKeyEnum.HERO_RECRUIT_POINTS_CHEST_BOOL.getDefaultValue());
        assertEquals("false",ConfigurationKeyEnum.HERO_RECRUIT_ADVANCED_KEYS_BOOL.getDefaultValue());
        assertEquals("false",ConfigurationKeyEnum.HERO_RECRUIT_EPIC_KEYS_BOOL.getDefaultValue());
    }
}
