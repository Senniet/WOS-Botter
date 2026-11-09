package dev.frostguard.update;

import dev.frostguard.api.runtime.RuntimeChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UpdateEndpointResolverTest {
    private final UpdateEndpointResolver resolver = new UpdateEndpointResolver();

    @AfterEach
    void clearProperties() {
        System.clearProperty(UpdateEndpointResolver.STABLE_PROPERTY);
        System.clearProperty(UpdateEndpointResolver.NIGHTLY_PROPERTY);
        System.clearProperty("survivalops.update.manifest.stable");
        System.clearProperty("frostguard.brand.name");
    }

    @Test
    void developmentHasNoEndpoint() throws Exception {
        assertFalse(resolver.resolve(RuntimeChannel.DEVELOPMENT).isPresent());
    }

    @Test
    void resolvesChannelSpecificHttpsEndpoint() throws Exception {
        System.setProperty(UpdateEndpointResolver.STABLE_PROPERTY, "https://updates.example.com/stable.json");
        assertEquals("https://updates.example.com/stable.json",
                resolver.resolve(RuntimeChannel.STABLE).orElseThrow().toString());
    }

    @Test
    void survivalOpsUsesDedicatedStableReleaseFeedAndHasNoNightlyFallback() throws Exception {
        System.setProperty("frostguard.brand.name", "SurvivalOps");
        assertEquals("https://github.com/Senniet/WOS-Botter/releases/download/survivalops-stable/survivalops-stable-manifest.json",
                resolver.resolve(RuntimeChannel.STABLE).orElseThrow().toString());
        assertFalse(resolver.resolve(RuntimeChannel.NIGHTLY).isPresent());
    }

    @Test
    void rejectsInsecureEndpoint() {
        System.setProperty(UpdateEndpointResolver.STABLE_PROPERTY, "http://updates.example.com/stable.json");
        assertThrows(UpdateException.class, () -> resolver.resolve(RuntimeChannel.STABLE));
    }
}
