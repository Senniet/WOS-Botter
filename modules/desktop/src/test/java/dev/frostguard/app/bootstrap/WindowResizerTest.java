package dev.frostguard.app.bootstrap;

import javafx.scene.Cursor;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Headless-safe checks of undecorated JavaFX resize fallback. */
class WindowResizerTest {
    @Test void recognizesEachCornerAndAllFourEdgesWithoutNativeWindowsHook() {
        double w = 960, h = 560;
        assertSame(Cursor.NW_RESIZE, WindowResizer.cursorForPosition(2,2,w,h));
        assertSame(Cursor.NE_RESIZE, WindowResizer.cursorForPosition(w-2,2,w,h));
        assertSame(Cursor.SW_RESIZE, WindowResizer.cursorForPosition(2,h-2,w,h));
        assertSame(Cursor.SE_RESIZE, WindowResizer.cursorForPosition(w-2,h-2,w,h));
        assertSame(Cursor.W_RESIZE, WindowResizer.cursorForPosition(2,200,w,h));
        assertSame(Cursor.E_RESIZE, WindowResizer.cursorForPosition(w-2,200,w,h));
        assertSame(Cursor.N_RESIZE, WindowResizer.cursorForPosition(200,2,w,h));
        assertSame(Cursor.S_RESIZE, WindowResizer.cursorForPosition(200,h-2,w,h));
        assertSame(Cursor.DEFAULT, WindowResizer.cursorForPosition(400,200,w,h));
    }

    @Test void widerResizeAreaIsUsableOnHighDpiAndDoesNotStealNormalButtons() {
        assertSame(Cursor.W_RESIZE, WindowResizer.cursorForPosition(9,150,960,560));
        assertSame(Cursor.E_RESIZE, WindowResizer.cursorForPosition(951,150,960,560));
        assertSame(Cursor.DEFAULT, WindowResizer.cursorForPosition(15,150,960,560));
        assertSame(Cursor.DEFAULT, WindowResizer.cursorForPosition(900,100,960,560));
        assertSame(Cursor.DEFAULT, WindowResizer.cursorForPosition(-2,120,960,560));
        assertSame(Cursor.DEFAULT, WindowResizer.cursorForPosition(960,120,960,560));
        assertSame(Cursor.DEFAULT, WindowResizer.cursorForPosition(Double.NaN,120,960,560));
    }

    @Test void sceneCapturesPressAndDragBeforeChildControlsCanConsumeThem() throws Exception {
        Path source = Path.of("src/main/java/dev/frostguard/app/bootstrap/WindowResizer.java");
        String code = Files.readString(source);
        assertTrue(code.contains("addEventFilter(MouseEvent.MOUSE_PRESSED, listener)"));
        assertTrue(code.contains("addEventFilter(MouseEvent.MOUSE_DRAGGED, listener)"));
        assertTrue(code.contains("addEventFilter(MouseEvent.MOUSE_RELEASED, listener)"));
        assertTrue(code.contains("cursorForPosition(mx, my, sw, sh)"));
        assertTrue(code.contains("if (!e.isPrimaryButtonDown()) return;"),
                "Only the primary mouse button should start an edge drag");
        String app = Files.readString(Path.of("src/main/java/dev/frostguard/app/bootstrap/FXApp.java"));
        assertTrue(app.contains("stage.setResizable(true)"),
                "Undecorated Windows stage must not be marked fixed-size");
        assertTrue(app.contains("WindowResizer.makeResizable(stage)"),
                "JavaFX resize fallback must always be available");
    }
}
