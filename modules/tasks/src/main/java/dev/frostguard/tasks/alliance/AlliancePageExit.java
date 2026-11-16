package dev.frostguard.tasks.alliance;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Bounded, 720x1280 page-specific Alliance Tech → Alliance → City exit. */
final class AlliancePageExit {
    private static final PointData BACK_MIN=new PointData(0,0),BACK_MAX=new PointData(110,105);
    private static final PointData TECH_MIN=new PointData(85,10),TECH_MAX=new PointData(245,75);
    private static final PointData ALLIANCE_MIN=new PointData(85,10),ALLIANCE_MAX=new PointData(270,75);
    private static final PointData CITY_MIN=new PointData(590,1145),CITY_MAX=new PointData(715,1275);
    private AlliancePageExit(){}
    enum Page { TECH, ALLIANCE, CITY, UNKNOWN }
    enum Result { HOME, NOT_ALLIANCE, UNVERIFIED }
    static Page page(RawImageData screen) {
        if (screen==null||!screen.isValid()||screen.getWidth()!=720||screen.getHeight()!=1280)
            return Page.UNKNOWN;
        if (match(screen,TemplatesEnum.SERVE_EMU_CITY_ANCHOR,CITY_MIN,CITY_MAX,90))
            return Page.CITY;
        if (!match(screen,TemplatesEnum.SERVE_EMU_ALLIANCE_BACK_LIVE_2026,BACK_MIN,BACK_MAX,90))
            return Page.UNKNOWN;
        if (match(screen,TemplatesEnum.SERVE_EMU_ALLIANCE_TECH_HEADER_LIVE_2026,TECH_MIN,TECH_MAX,91))
            return Page.TECH;
        return match(screen,TemplatesEnum.SERVE_EMU_ALLIANCE_HEADER_LIVE_2026,
                ALLIANCE_MIN,ALLIANCE_MAX,91)?Page.ALLIANCE:Page.UNKNOWN;
    }
    static Result returnHome(Supplier<RawImageData> capture, Consumer<PointData> tap,Runnable settle) {
        Page first=page(capture.get());
        if(first==Page.CITY)return Result.HOME;
        if(first==Page.UNKNOWN)return Result.NOT_ALLIANCE;
        for(int i=0;i<2;i++){
            settle.run();
            Page second=page(capture.get());
            if(second!=first)return Result.UNVERIFIED;
            // Verified page title AND matching back arrow on two fresh frames.
            tap.accept(new PointData(43,36));
            settle.run();
            first=page(capture.get());
            if(first==Page.CITY)return Result.HOME;
            if(first==Page.UNKNOWN)return Result.UNVERIFIED;
        }
        return Result.UNVERIFIED;
    }
    private static boolean match(RawImageData f,TemplatesEnum t,PointData a,PointData b,int th){
        return OpenCvPatternLocator.locatePattern(f,t.getTemplate(),a,b,th).isFound();
    }
}
