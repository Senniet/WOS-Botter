package dev.frostguard.engine.config;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Ensures the draggable web rank string is honored by actual Java research. */
class ResearchPriorityOrderTest {
    private static final ConfigurationKeyEnum KEY = ConfigurationKeyEnum.RESEARCH_PRIORITIES_STRING;

    @Test void economyFirstMeansResearchRoutineTriesEconomyBeforeGrowthAndBattle() {
        var profile=new AccountDescriptor(1L);
        profile.setConfig(KEY,"economy:Economy:1:true|growth:Growth:2:true|battle:Battle:3:true");
        var actual=PriorityConfigResolver.activeRankings(profile,KEY);
        assertEquals(List.of("economy","growth","battle"),actual.stream().map(x->x.getIdentifier()).toList());
        assertEquals(List.of(1,2,3),actual.stream().map(x->x.getPriority()).toList());
    }

    @Test void orderPersistsEvenWhenRawPartsAreNotSortedAndDisabledRowIsSkipped() {
        var profile=new AccountDescriptor(1L);
        profile.setConfig(KEY,"growth:Growth:3:true|battle:Battle:2:false|economy:Economy:1:true");
        assertEquals(List.of("economy","growth"),PriorityConfigResolver.activeRankings(profile,KEY)
                .stream().map(x->x.getIdentifier()).toList());
        assertEquals(3,PriorityConfigResolver.allRankings(profile,KEY).size());
    }

    @Test void alternativeOrderedListIsNotHardcodedToOriginalDefault() {
        var profile=new AccountDescriptor(1L);
        profile.setConfig(KEY,"battle:Battle:1:true|economy:Economy:2:true|growth:Growth:3:true");
        assertEquals(List.of("battle","economy","growth"),PriorityConfigResolver.activeRankings(profile,KEY)
                .stream().map(x->x.getIdentifier()).toList());
    }
}
