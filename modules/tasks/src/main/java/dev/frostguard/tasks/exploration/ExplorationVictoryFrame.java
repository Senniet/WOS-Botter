package dev.frostguard.tasks.exploration;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.api.domain.OcrSettingsData;
import dev.frostguard.vision.convert.ImageConverter;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import dev.frostguard.vision.ocr.OcrEngine;
import dev.frostguard.vision.ocr.OcrException;

import java.awt.image.BufferedImage;
import java.util.Locale;

/**
 * The normal Exploration Victory shows two neighboring blue actions:
 * LEFT Return to City and RIGHT Continue. Only the left one may be used
 * for recovery; never infer the intended action from the blue color alone.
 */
final class ExplorationVictoryFrame {
    // Captured on 2026-11-19 directly from Serve-Emu 720x1280 Victory.
    private static final PointData VICTORY_TITLE_START = new PointData(80,190);
    private static final PointData VICTORY_TITLE_END = new PointData(645,550);
    private static final PointData RETURN_ROI_START = new PointData(0,920);
    private static final PointData RETURN_ROI_END = new PointData(420,1085);
    private static final PointData RETURN_TEXT_START = new PointData(67,983);
    private static final PointData RETURN_TEXT_END = new PointData(318,1042);
    // 2026-11-19 live frame: blue button x46..339, y976..1053.
    // Both strips are clear of the label text and the adjacent Continue.
    private static final int[] LEFT_BLUE = {55,985,83,1037};
    private static final int[] RIGHT_BLUE = {300,986,324,1036};

    private ExplorationVictoryFrame(){}

    static boolean visible(RawImageData f) {
        if(f==null || !f.isValid() || f.getWidth()!=720 || f.getHeight()!=1280)
            return false;
        return OpenCvPatternLocator.locatePattern(f,
                TemplatesEnum.EXPLORATION_VICTORY.getTemplate(),
                VICTORY_TITLE_START,VICTORY_TITLE_END,90).isFound();
    }

    static boolean containsReturnToCityLabel(String text) {
        if(text==null)return false;
        // Tesseract reads the letter edges and dark outline as
        // "Return|to/City" or "Return\\to City" on current live frames.
        String normalized=text.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return normalized.equals("returntocity");
    }

    private static boolean blueStrip(BufferedImage image,int[] region) {
        int blue=0,total=0;
        for(int y=region[1];y<region[3];y+=3)
            for(int x=region[0];x<region[2];x+=3) {
                int color=image.getRGB(x,y);
                int r=color>>>16&255,g=color>>>8&255,b=color&255;
                if(b>205 && g>110 && r<175 && b-r>75)blue++;
                total++;
            }
        return total>0 && blue*100>=85*total;
    }

    static ImageSearchResultData returnToCity(RawImageData f) {
        if(!visible(f)) return ImageSearchResultData.miss();

        var exact=OpenCvPatternLocator.locatePattern(f,
                TemplatesEnum.SERVE_EMU_EXPLORATION_VICTORY_RETURN_CITY_2026.getTemplate(),
                RETURN_ROI_START, RETURN_ROI_END,90);
        if(exact.isFound() && exact.getPoint()!=null && exact.getPoint().getX()<355)
            return exact;

        // The newer live button artwork differs from the saved template.
        // Require two independent BLUE strips in the left button AND
        // the OCR text, not merely a blue rectangle or the neighboring Continue.
        BufferedImage image=ImageConverter.toBufferedImage(f);
        if(!blueStrip(image,LEFT_BLUE) || !blueStrip(image,RIGHT_BLUE))
            return ImageSearchResultData.miss();
        try {
            String text=OcrEngine.recognizeText(f,RETURN_TEXT_START,RETURN_TEXT_END,
                    OcrSettingsData.assembler()
                            .textLayout(OcrSettingsData.TextLayout.SINGLE_LINE).build());
            return containsReturnToCityLabel(text)
                    ? ImageSearchResultData.hit(190,1015,96,245,42)
                    : ImageSearchResultData.miss();
        } catch (OcrException | RuntimeException unreadable) {
            return ImageSearchResultData.miss();
        }
    }
}
