package dev.frostguard.tasks.dailies;

import dev.frostguard.api.domain.OcrSettingsData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.ocr.OcrEngine;
import dev.frostguard.vision.ocr.OcrException;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** An exact named overlay; not a generic Rewards, store or paid confirmation. */
final class TriumphWeeklyRewardsFrame {
    private static final PointData TITLE_A=new PointData(120,320);
    private static final PointData TITLE_B=new PointData(590,420);
    private static final PointData EXIT_A=new PointData(155,1150);
    private static final PointData EXIT_B=new PointData(580,1230);
    private static final PointData SAFE_EXIT=new PointData(360,1180);
    private static final OcrSettingsData OCR=OcrSettingsData.forTextBlock();
    private TriumphWeeklyRewardsFrame(){}

    static boolean hasOverlayText(String title,String footer){
        return compact(title).contains("weeklyrewards")
                && compact(footer).contains("tapanywheretoclose");
    }
    private static String compact(String text){
        return text==null?"":text.toLowerCase(Locale.ROOT).replaceAll("[^a-z]","");
    }
    static boolean visible(RawImageData frame){
        if(frame==null || !frame.isValid() || frame.getWidth()!=720 || frame.getHeight()!=1280)
            return false;
        try {
            return hasOverlayText(
                    OcrEngine.recognizeText(frame,TITLE_A,TITLE_B,OCR),
                    OcrEngine.recognizeText(frame,EXIT_A,EXIT_B,OCR));
        } catch(OcrException invalid){return false;}
    }
    enum Result { ABSENT, DISMISSED, UNVERIFIED }

    static Result dismiss(Supplier<RawImageData> capture,Consumer<PointData> tap,Runnable settle){
        if(!visible(capture.get()))return Result.ABSENT;
        settle.run();
        if(!visible(capture.get()))return Result.UNVERIFIED;
        tap.accept(SAFE_EXIT);
        settle.run();
        return visible(capture.get())?Result.UNVERIFIED:Result.DISMISSED;
    }
}
