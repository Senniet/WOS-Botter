package dev.frostguard.tasks.combat;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;

/** Live 720x1280 Arena action and outcome recognition.
 * Screenshots and button locations were measured on emulator-5554 on
 * 2026-11-17. No remote capture is taken by this detector, allowing the bot
 * to inspect several targets on a single fresh frame before acting.
 */
final class ArenaLiveFrame {
    enum Result { NONE, VICTORY, DEFEAT, UNKNOWN }
    private ArenaLiveFrame() {}

    private static boolean valid(RawImageData frame) {
        return frame!=null && frame.isValid() && frame.getWidth()==720 && frame.getHeight()==1280;
    }

    static boolean formation(RawImageData frame) {
        return valid(frame) && match(frame, TemplatesEnum.SERVE_EMU_ARENA_FORMATION_TITLE_2026,
                70,0,600,100,90).isFound();
    }

    static ImageSearchResultData equalize(RawImageData frame) {
        if (!formation(frame)) return ImageSearchResultData.miss();
        // 20 Nov: Balance is a THIRD button, and Equalize has moved left.
        // Previous x220..375 ROI could never contain its complete label.
        ImageSearchResultData current = match(frame,
                TemplatesEnum.SERVE_EMU_ARENA_EQUALIZE_NOV20_CURRENT,
                130,1115,295,1275,90);
        return current.isFound() ? current : match(frame,
                TemplatesEnum.SERVE_EMU_ARENA_EQUALIZE_2026,
                220,1125,375,1275,89);
    }

    static ImageSearchResultData fight(RawImageData frame) {
        return !formation(frame) ? ImageSearchResultData.miss()
                : match(frame,TemplatesEnum.SERVE_EMU_ARENA_FIGHT_2026,
                        385,1145,715,1275,88);
    }

    static boolean balanced(RawImageData frame) {
        if (!valid(frame) || !formation(frame) || frame.getBytesPerPixel()!=4)return false;
        // On the observed 2026 formation a successfully equalized roster has
        // three green allocation bars, rather than one green Infantry bar and
        // two grey empty Lancer/Marksman bars.
        for (int y:new int[]{720,860,999}) {
            int offset=(y*720+280)*4;
            byte[] pixels=frame.getData();
            int r=pixels[offset]&255,g=pixels[offset+1]&255,b=pixels[offset+2]&255;
            if (!(g>=135 && g>r+45 && g>b+25)) return false;
        }
        return true;
    }

    static Result outcome(RawImageData frame) {
        if(!valid(frame))return Result.NONE;
        // No battle result may be acted upon without the explicit lower
        // 'Tap anywhere to exit' message in the ACTUAL Arena result screen.
        var exit=match(frame,TemplatesEnum.SERVE_EMU_ARENA_RESULT_EXIT_2026,
                185,1160,560,1270,86);
        if(!exit.isFound()) exit=matchMono(frame,TemplatesEnum.SERVE_EMU_ARENA_RESULT_EXIT_2026,
                185,1160,560,1270,86);
        if(!exit.isFound())return Result.NONE;
        if(match(frame,TemplatesEnum.ARENA_VICTORY,130,270,600,535,88).isFound())
            return Result.VICTORY;
        if(match(frame,TemplatesEnum.SERVE_EMU_ARENA_DEFEAT_2026,
                170,245,560,430,89).isFound())return Result.DEFEAT;
        return Result.UNKNOWN;
    }

    static boolean arenaHome(RawImageData frame) {
        // The current live title reads "Arena of Expedition"; the older
        // template from a former layout scores below threshold on it.
        // Prefer the exact new live screenshot crop before legacy fallback.
        return valid(frame)
                && (match(frame, TemplatesEnum.SERVE_EMU_ARENA_HOME_TITLE_NOV_2026,
                        70,0,490,115,90).isFound()
                    || match(frame,TemplatesEnum.SERVE_EMU_ARENA_TITLE,
                        70,0,470,105,90).isFound());
    }

    static boolean cityOrWorld(RawImageData frame) {
        return valid(frame) && (match(frame,TemplatesEnum.SERVE_EMU_CITY_ANCHOR,
                575,1125,720,1280,90).isFound()
                || match(frame,TemplatesEnum.SERVE_EMU_WORLD_ANCHOR_CURRENT,
                575,1125,720,1280,90).isFound());
    }

    static ImageSearchResultData listClose(RawImageData frame) {
        return challengeList(frame) ? match(frame,TemplatesEnum.SERVE_EMU_ARENA_LIST_CLOSE_2026,
                625,145,712,235,90) : ImageSearchResultData.miss();
    }

    static boolean challengeList(RawImageData frame) {
        return valid(frame)
                && (match(frame, TemplatesEnum.SERVE_EMU_ARENA_LIST_TITLE_NOV20_CURRENT,
                        220,125,535,270,90).isFound()
                    || match(frame,TemplatesEnum.SERVE_EMU_ARENA_LIST_TITLE,
                        220,140,530,260,90).isFound());
    }

    private static ImageSearchResultData match(RawImageData frame,TemplatesEnum t,
                                               int x0,int y0,int x1,int y1,double threshold) {
        return OpenCvPatternLocator.locatePattern(frame,t.getTemplate(),
                new PointData(x0,y0),new PointData(x1,y1),threshold);
    }
    private static ImageSearchResultData matchMono(RawImageData frame,TemplatesEnum t,
                                               int x0,int y0,int x1,int y1,double threshold) {
        return OpenCvPatternLocator.locatePatternMono(frame,t.getTemplate(),
                new PointData(x0,y0),new PointData(x1,y1),threshold);
    }
}
