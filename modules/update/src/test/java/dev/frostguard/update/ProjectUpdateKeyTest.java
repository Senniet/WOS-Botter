package dev.frostguard.update;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectUpdateKeyTest {
    @AfterEach
    void clearBrand() {
        System.clearProperty("frostguard.brand.name");
    }

    @Test
    void survivalOpsUsesItsOwnUnprovisionedTrustAnchorInsteadOfFrostguardKey() {
        System.setProperty("frostguard.brand.name", "SurvivalOps");
        ManifestVerificationKey survivalOps = ProjectUpdateKey.current();
        assertEquals("survivalops-update-2026-01", survivalOps.keyId());
        assertFalse(survivalOps.isUsable());

        System.setProperty("frostguard.brand.name", "Frostguard");
        assertTrue(ProjectUpdateKey.current().isUsable());
    }

    @Test
    void readsCommittedPublicTrustAnchor() {
        ManifestVerificationKey expected = TestManifestKeys.trustedKey();
        String properties = "keyId=" + expected.keyId() + "\npublicKey=" + expected.publicKeyBase64();
        ManifestVerificationKey actual = ProjectUpdateKey.read(new ByteArrayInputStream(
                properties.getBytes(StandardCharsets.UTF_8)));

        assertEquals(expected, actual);
        assertTrue(actual.isUsable());
        assertFalse(ProjectUpdateKey.read(null).isUsable());
    }
}
