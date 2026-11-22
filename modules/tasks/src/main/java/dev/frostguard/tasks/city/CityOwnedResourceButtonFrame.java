package dev.frostguard.tasks.city;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

/**
 * Detect the 720x1280 modern "Obtain more" page and distinguish the disabled
 * grey "Replenish All" button from an enabled (blue/green) one.
 * The disabled state was captured live on 20 Nov 2026, when tapping the old
 * hard-coded confirmation (511,1056) instead opened VIP Shop.
 * This alone NEVER authorizes a purchase or a secondary confirmation.
 */
final class CityOwnedResourceButtonFrame {
    private static final int[][] BUTTON_BACKGROUND = {
            {260, 1120}, {460, 1120}, {260, 1145}, {460, 1145}
    };

    private CityOwnedResourceButtonFrame() {}

    static boolean isObtainMore(RawImageData frame) {
        return valid(frame) && OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.SERVE_EMU_OBTAIN_MORE_TITLE_NOV_2026.getTemplate(),
                new PointData(210, 70), new PointData(525, 205), 92).isFound();
    }

    static boolean hasEnabledResourceButton(RawImageData frame) {
        if (!isObtainMore(frame)) return false;
        int colored=0;
        for (int[] p : BUTTON_BACKGROUND) {
            int r=channel(frame,p[0],p[1],0),g=channel(frame,p[0],p[1],1),
                    b=channel(frame,p[0],p[1],2);
            // Allow only blue and green enablement; no orange premium buttons.
            boolean blue=b>=154 && b-r>=55 && b-g>=14 && g>=95;
            boolean green=g>=140 && g-r>=48 && g-b>=22;
            if (blue || green) colored++;
        }
        // Require three independent points. Grey disabled controls score 0.
        return colored>=3;
    }

    private static boolean valid(RawImageData frame) {
        return frame!=null && frame.isValid() && frame.getWidth()==720
                && frame.getHeight()==1280 && frame.getBytesPerPixel()==4;
    }

    private static int channel(RawImageData frame,int x,int y,int c) {
        return frame.getData()[(y*720+x)*4+c]&0xff;
    }
}
