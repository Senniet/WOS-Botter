package dev.frostguard.tasks.alliance;

import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.engine.helper.GreenLabelActionDetector;
import java.awt.image.BufferedImage;

/** Button rectangles recorded on the live Serve-EMU 720x1280 Alliance Chests. */
final class AllianceChestLiveFrame {
    private AllianceChestLiveFrame() {}

    static ImageSearchResultData findGiftClaim(BufferedImage frame) {
        for (int top : new int[]{608,760,912,1064}) {
            var found = GreenLabelActionDetector.detect(frame,
                    new GreenLabelActionDetector.Region(532,top,677,top+64));
            if (found.isFound()) return found;
        }
        return ImageSearchResultData.miss();
    }

    static ImageSearchResultData findLootClaimAll(BufferedImage frame) {
        return GreenLabelActionDetector.detect(frame,
                new GreenLabelActionDetector.Region(220,1167,500,1250));
    }

    static boolean keyProgressChanged(BufferedImage before, BufferedImage after) {
        if (before==null || after==null || before.getWidth()!=720
                || before.getHeight()!=1280 || after.getWidth()!=720
                || after.getHeight()!=1280) return false;
        int changed=0;
        for (int y=287;y<324;y+=2)
            for (int x=234;x<450;x+=2) {
                int a=before.getRGB(x,y),b=after.getRGB(x,y);
                int delta=Math.abs((a>>16&255)-(b>>16&255))
                        +Math.abs((a>>8&255)-(b>>8&255))
                        +Math.abs((a&255)-(b&255));
                if (delta>110) changed++;
            }
        return changed>=14;
    }
}
