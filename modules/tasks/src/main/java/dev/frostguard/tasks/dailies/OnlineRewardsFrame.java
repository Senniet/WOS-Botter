package dev.frostguard.tasks.dailies;

import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.OcrSettingsData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.engine.helper.GreenLabelActionDetector;
import dev.frostguard.tasks.diagnostics.TaskControlSignals;
import dev.frostguard.vision.convert.ImageConverter;
import dev.frostguard.vision.ocr.OcrEngine;
import dev.frostguard.vision.ocr.OcrException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Conservative OCR probe for current sidebar & online reward page. */
final class OnlineRewardsFrame {
    private static final OcrSettingsData TEXT=OcrSettingsData.configurator()
            .textLayout(OcrSettingsData.TextLayout.TEXT_BLOCK).build();
    private OnlineRewardsFrame() {}
    static String text(RawImageData frame,int l,int t,int r,int b){
        if(frame==null||!frame.isValid()||frame.getWidth()!=720||frame.getHeight()!=1280)return "";
        try{return OcrEngine.recognizeText(frame,new PointData(l,t),new PointData(r,b),TEXT);}
        catch(OcrException expected){return "";}
        catch(RuntimeException exception){TaskControlSignals.rethrowControlSignal(exception);return "";}
    }
    static int onlineRewardsRow(RawImageData sidebar){
        if(!DailyOverviewFrame.isDailyOverview(sidebar))return -1;
        String whole=DailyOverviewFrame.readVisibleText(sidebar).toUpperCase(Locale.ROOT);
        if(!whole.contains("ONLINE")||!whole.contains("REWARD"))return -1;
        for(int y=304;y<=920;y+=56){
            String row=text(sidebar,18,y,450,Math.min(1080,y+105)).toUpperCase(Locale.ROOT);
            if(row.contains("ONLINE")&&row.contains("REWARD"))return y+55;
        }
        return -1;
    }
    static boolean dailyOnlineReady(String observed){
        if(observed==null)return false;
        String v=observed.toUpperCase(Locale.ROOT).replaceAll("\\s+"," ");
        return Pattern.compile("ONLINE\\s+REWARDS?.{0,65}?(?:READY|CLAIM(?:ABLE)?|AVAILABLE)")
                .matcher(v).find();
    }
    static boolean onlinePage(RawImageData frame){
        return text(frame,42,0,640,220).toUpperCase(Locale.ROOT).contains("ONLINE REWARD");
    }
    static boolean freeClaimVisible(RawImageData frame){
        if(!onlinePage(frame))return false;
        String body=text(frame,45,275,675,1180).toUpperCase(Locale.ROOT);
        return body.contains("CLAIM") && body.contains("FREE")
                && !Pattern.compile("(?:US\\$|\\$\\d|PURCHASE|BUY NOW|GEMS)").matcher(body).find();
    }
    static ImageSearchResultData freeClaim(RawImageData frame){
        if(!freeClaimVisible(frame))return ImageSearchResultData.miss();
        // Candidate must have a *green body and white label* in one small
        // anchored region. A label in text alone is never actionable.
        var image=ImageConverter.toBufferedImage(frame);
        for(int y=540;y<=1140;y+=55){
            for(int x=145;x<=380;x+=90){
                var candidate=GreenLabelActionDetector.detect(image,
                        new GreenLabelActionDetector.Region(x,y,x+230,Math.min(1260,y+70)));
                if(candidate.isFound())return candidate;
            }
        }
        return ImageSearchResultData.miss();
    }
}
