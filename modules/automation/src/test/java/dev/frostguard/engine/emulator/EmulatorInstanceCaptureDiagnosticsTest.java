package dev.frostguard.engine.emulator;

import org.junit.jupiter.api.Test;

import dev.frostguard.api.domain.RawImageData;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmulatorInstanceCaptureDiagnosticsTest {

    @Test
    void monitorAndTaskShareACaptureLockButOtherDevicesRemainIndependent() throws Exception {
        ReentrantLock first = EmulatorInstance.captureLockForSerial("192.168.1.20:15559");
        assertSame(first, EmulatorInstance.captureLockForSerial("192.168.1.20:15559"));
        assertNotSame(first, EmulatorInstance.captureLockForSerial("emulator-5554"));
        CountDownLatch attempted = new CountDownLatch(1);
        AtomicBoolean entered = new AtomicBoolean(false);
        first.lock();
        try {
            Thread other = new Thread(() -> {
                attempted.countDown();
                first.lock();
                try { entered.set(true); } finally { first.unlock(); }
            });
            other.start();
            assertTrue(attempted.await(2, TimeUnit.SECONDS));
            assertTrue(!entered.get(), "Second capture entered while first holds the serial lock");
            assertTrue(EmulatorInstance.captureLockForSerial("emulator-5554").tryLock());
            EmulatorInstance.captureLockForSerial("emulator-5554").unlock();
        } finally {
            first.unlock();
        }
    }

    @Test
    void captureFailureRetainsAdbTargetCommandBytesCauseAndExitAvailability() {
        String message = EmulatorInstance.captureFailureMessage(
                "1", "127.0.0.1:16416", 4096,
                new IllegalStateException("pixel payload was incomplete"));

        assertTrue(message.contains("emulator=1"));
        assertTrue(message.contains("serial=127.0.0.1:16416"));
        assertTrue(message.contains("command=screencap"));
        assertTrue(message.contains("commandTimeoutMs=5000"));
        assertTrue(message.contains("receivedBytes=4096"));
        assertTrue(message.contains("exitDetail=ddmlib shell capture exposes no process exit code"));
        assertTrue(message.contains("cause=IllegalStateException: pixel payload was incomplete"));
    }
    @Test
    void decodesRgbaScreenshotWithExactPayload() {
        byte[] payload = new byte[12 + 2 * 2 * 4];
        putInt(payload, 0, 2);
        putInt(payload, 4, 2);
        putInt(payload, 8, 1); // RGBA_8888
        RawImageData frame = EmulatorInstance.decodeScreenshotPayload(payload);
        assertEquals(2, frame.getWidth());
        assertEquals(2, frame.getHeight());
        assertEquals(32, frame.getBpp());
        assertTrue(frame.isValid());
    }

    @Test
    void decodesRgb565Screenshot() {
        byte[] payload = new byte[12 + 3 * 2];
        putInt(payload, 0, 3);
        putInt(payload, 4, 1);
        putInt(payload, 8, 4); // RGB_565
        RawImageData frame = EmulatorInstance.decodeScreenshotPayload(payload);
        assertEquals(16, frame.getBpp());
        assertTrue(frame.isValid());
    }

    @Test
    void rejectsTruncatedScreenshotInsteadOfReturningCorruptFrame() {
        byte[] payload = new byte[12 + 3];
        putInt(payload, 0, 2);
        putInt(payload, 4, 2);
        putInt(payload, 8, 1);
        assertThrows(IllegalArgumentException.class,
                () -> EmulatorInstance.decodeScreenshotPayload(payload));
    }

    @Test
    void rejectsUnknownPixelFormatInsteadOfAssumingRgb565() {
        byte[] payload = new byte[16];
        putInt(payload, 0, 1);
        putInt(payload, 4, 1);
        putInt(payload, 8, 99);
        assertThrows(IllegalArgumentException.class,
                () -> EmulatorInstance.decodeScreenshotPayload(payload));
    }

    private static void putInt(byte[] target, int offset, int value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >>> 8);
        target[offset + 2] = (byte) (value >>> 16);
        target[offset + 3] = (byte) (value >>> 24);
    }

}
