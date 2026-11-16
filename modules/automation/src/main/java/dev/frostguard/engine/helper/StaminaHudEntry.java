package dev.frostguard.engine.helper;

import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.convert.ImageConverter;
import java.awt.image.BufferedImage;

/**
 * The 2026 arrow at (34,140) opens City Bonus, NOT stamina.
 * Stamina is the green bar directly above it (around 42,106).
 * Only authorize that small rectangle when an actual green bar is present.
 * The caller must independently verify a City/World root anchor.
 */
final class StaminaHudEntry {
    private static final PointData BAR_CENTER = new PointData(42,106);
    private StaminaHudEntry() {}

    static PointData verifiedTarget(RawImageData frame) {
        if (frame==null || !frame.isValid() || frame.getWidth()!=720 || frame.getHeight()!=1280)
            return null;
        BufferedImage image=ImageConverter.toBufferedImage(frame);
        int green=0;
        for(int y=99;y<=114;y++)for(int x=22;x<=75;x++){
            int c=image.getRGB(x,y);
            int r=(c>>16)&255, g=(c>>8)&255, b=c&255;
            if(g>=105 && g>r*1.30 && g>b*1.20)green++;
        }
        return green>=220?BAR_CENTER:null;
    }
}
