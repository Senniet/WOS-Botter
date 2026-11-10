package dev.frostguard.app.panel.launcher;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Static contracts for UI resources. These tests work on headless CI as well:
 * no JavaFX graphics toolkit or DISPLAY is required to catch mismatched fx:ids,
 * missing handlers, or accidentally reintroduced placeholder status claims.
 */
class SurvivalOpsLayoutContractTest {
    private static String readResource(String name) throws Exception {
        try (InputStream in = SurvivalOpsLayoutContractTest.class.getResourceAsStream(name)) {
            assertNotNull(in, "Missing resource " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Document document(String name) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://javax.xml.XMLConstants/feature/secure-processing", true);
        try (InputStream in = SurvivalOpsLayoutContractTest.class.getResourceAsStream(name)) {
            assertNotNull(in);
            return factory.newDocumentBuilder().parse(in);
        }
    }

    @Test
    void dashboardFxmlProvidesAllControllerBindingsAndActions() throws Exception {
        Document doc = document("/layout/DashboardLayout.fxml");
        Set<String> methods = Arrays.stream(DashboardLayoutController.class.getDeclaredMethods())
                .map(Method::getName).collect(Collectors.toSet());
        Set<String> fields = Arrays.stream(DashboardLayoutController.class.getDeclaredFields())
                .map(Field::getName).collect(Collectors.toSet());
        NodeList nodes = doc.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String id = element.getAttributeNS("http://javafx.com/fxml/1", "id");
            if (!id.isEmpty()) assertTrue(fields.contains(id), "Unknown dashboard fx:id: " + id);
            String action = element.getAttribute("onAction");
            if (action.startsWith("#")) {
                assertTrue(methods.contains(action.substring(1)), "Missing dashboard action: " + action);
            }
        }
        String text = readResource("/layout/DashboardLayout.fxml");
        assertTrue(text.contains("<?import javafx.scene.control.*?>"));
        assertTrue(text.contains("<?import javafx.scene.image.*?>"));
        assertTrue(text.contains("<?import javafx.scene.layout.*?>"));
        assertFalse(text.contains("Standaard WOS Routine"), "Do not show simulated routine progress");
        assertFalse(text.contains("0.24"), "Do not show invented disk usage");
        assertNotNull(getClass().getResource("/icons/survivalops-banner.png"));
    }

    @Test
    void globalShellHasNoEmptyHeaderOrFabricatedConnectionStatus() throws Exception {
        String shell = readResource("/layout/LauncherLayout.fxml");
        assertFalse(shell.contains("heroBanner"), "Duplicate empty top header");
        assertFalse(shell.contains("Alle systemen operationeel"), "Unverified health claim");
        assertFalse(shell.contains("ADB: Verbonden"), "Unverified ADB claim");
        assertFalse(shell.contains("Updates: beschikbaar"), "Unverified update claim");
        assertTrue(shell.contains("sidebarStatusTitle"));
        assertTrue(shell.contains("logoContainer"), "Quick navigation logo must remain linked");
        assertTrue(shell.contains("footerBotStatus"));
        assertTrue(shell.contains("buttonStartStop"));
        assertNotNull(getClass().getResource("/icons/appIcon.png"));
    }
}
