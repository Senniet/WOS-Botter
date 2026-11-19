package dev.frostguard.tasks.dailies;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.OcrSettingsData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.tasks.diagnostics.TaskControlSignals;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import dev.frostguard.vision.ocr.OcrEngine;
import dev.frostguard.vision.ocr.OcrException;
import java.util.regex.Pattern;

/** 18-Nov-2026 measured 720x1280 Top-up Center -> Daily Deals. */
final class TopUpDailyDealsFrame {
    private TopUpDailyDealsFrame() {}
    private static final OcrSettingsData TEXT = OcrSettingsData.configurator()
            .textLayout(OcrSettingsData.TextLayout.TEXT_BLOCK).build();
    private static final Pattern COUNTDOWN=Pattern.compile("\\b\\d{1,2}\\s*:\\s*\\d{2}\\s*:\\s*\\d{2}\\b");
    static boolean valid(RawImageData f){return f!=null&&f.isValid()&&f.getWidth()==720&&f.getHeight()==1280;}
    static ImageSearchResultData match(RawImageData f,TemplatesEnum key,int l,int t,int r,int b,double threshold){
        return valid(f)?OpenCvPatternLocator.locatePattern(f,key.getTemplate(),
                new PointData(l,t),new PointData(r,b),threshold):ImageSearchResultData.miss();
    }
    static boolean city(RawImageData f){
        return match(f,TemplatesEnum.SERVE_EMU_CITY_ANCHOR,590,1145,715,1275,90).isFound();
    }
    static ImageSearchResultData cityCart(RawImageData f){
        if(!city(f))return ImageSearchResultData.miss();
        return match(f,TemplatesEnum.TOP_UP_CITY_CART_2026,600,34,720,140,88);
    }
    static boolean topUp(RawImageData f){
        return match(f,TemplatesEnum.TOP_UP_CENTER_HEADER_2026,66,0,475,100,90).isFound();
    }
    static ImageSearchResultData dailyDealsTab(RawImageData f){
        if(!topUp(f))return ImageSearchResultData.miss();
        return match(f,TemplatesEnum.TOP_UP_DAILY_DEALS_TAB_2026,10,87,716,195,88);
    }
    static boolean dailyDeals(RawImageData f){
        return topUp(f)&&match(f,TemplatesEnum.TOP_UP_DAILY_DEALS_TITLE_2026,
                20,170,390,265,88).isFound();
    }
    static boolean openedFreeChestOnCooldown(RawImageData f){
        if(!dailyDeals(f))return false;
        // The green-open chest with a countdown is a *claimed* free chest.
        // It must never be tapped again just because other paid packs exist.
        return match(f,TemplatesEnum.TOP_UP_DAILY_FREE_CHEST_2026,
                15,338,165,467,86).isFound();
    }
    static boolean cooldownText(RawImageData f){
        if(!dailyDeals(f))return false;
        try{
            String text=OcrEngine.recognizeText(f,new PointData(12,355),
                    new PointData(238,485),TEXT);
            return COUNTDOWN.matcher(text).find();
        }catch(OcrException expected){return false;}
        catch(RuntimeException unexpected){TaskControlSignals.rethrowControlSignal(unexpected);return false;}
    }
    /** Strict free chest readiness; generic blue paid packs never match this ROI. */
    static boolean claimedReceipt(RawImageData f){
        // The reward receipt blurs the entire Top-up Center: its header and
        // timer cannot be matched until the overlay is dismissed. This mode
        // is checked ONLY after a positively identified Free chest was tapped.
        return match(f,TemplatesEnum.TOP_UP_DAILY_CLAIMED_BANNER_2026,
                170,240,560,430,91).isFound()
            && match(f,TemplatesEnum.TOP_UP_DAILY_CLAIMED_EXIT_2026,
                150,1110,560,1250,90).isFound();
    }
    static boolean readyFreeChest(RawImageData f){
        if(!dailyDeals(f)||openedFreeChestOnCooldown(f)||cooldownText(f))return false;
        // Recognize a *closed* Daily Deals chest near the known free icon.
        // The closed artwork was observed in the Daily Deals tab; the first
        // ready body frame remains to be validated on the next daily reset.
        // New Nov 19 artwork contains the complete unopened chest and its
        // white "Free" label, unlike the older cropped close-up. It lives
        // only above y=500; paid pack buttons start below y=600.
        if(match(f,TemplatesEnum.TOP_UP_DAILY_READY_CHEST_2026,
                18,338,175,485,90).isFound())return true;
        return OpenCvPatternLocator.locatePatternMultiScale(f,
                TemplatesEnum.TOP_UP_DAILY_CLOSED_CHEST_2026.getTemplate(),
                new PointData(18,343),new PointData(170,467),90).isFound();
    }
}
