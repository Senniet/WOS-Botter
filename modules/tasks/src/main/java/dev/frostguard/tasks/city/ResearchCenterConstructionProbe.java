package dev.frostguard.tasks.city;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.AreaData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.engine.nav.CommonOCRSettings;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import dev.frostguard.vision.ocr.OcrEngine;
import java.time.Duration;

/**
 * A Research Center BUILDING upgrade is not an active Tech Research. In the
 * current 720x1280 City sidebar, Tech Research displays orange "Upgrading"
 * without a clock until the Research Center construction has completed.
 * Read the construction timer instead of trying 5 OCR passes on that word.
 * No UI taps and no premium speedups are involved.
 */
final class ResearchCenterConstructionProbe {
    private static final AreaData QUEUE_HEADERS=AreaData.of(66,314,405,487);
    private static final AreaData QUEUE_ONE_CLOCK=AreaData.of(130,374,340,411);
    private static final AreaData QUEUE_TWO_CLOCK=AreaData.of(130,444,342,479);
    private static final int HEADER_THRESHOLD=91;

    record Observation(int queue, Duration remaining, int headerY) {
        boolean hasTimer(){return remaining!=null;}
    }
    private ResearchCenterConstructionProbe(){}

    static Observation inspect(RawImageData frame) throws java.io.IOException, dev.frostguard.vision.ocr.OcrException {
        if(frame==null || !frame.isValid() || frame.getWidth()!=720 || frame.getHeight()!=1280)
            return null;
        var hit=OpenCvPatternLocator.locatePattern(frame,
                TemplatesEnum.RESEARCH_CENTER_CONSTRUCTION_2026.getTemplate(),
                QUEUE_HEADERS.topLeft(),QUEUE_HEADERS.bottomRight(),HEADER_THRESHOLD);
        if(!hit.isFound() || hit.getPoint()==null)return null;
        int y=hit.getPoint().getY();
        // The production queues have fixed vertical rows in the current City
        // panel. A recognized title anywhere else is not evidence of a queue.
        int queue = y>=350 && y<=400?1:y>=414 && y<=466?2:0;
        if(queue==0)return null;
        AreaData clock=queue==1?QUEUE_ONE_CLOCK:QUEUE_TWO_CLOCK;
        String raw=OcrEngine.recognizeText(frame,clock.topLeft(),clock.bottomRight(),
                CommonOCRSettings.MARCH_QUEUE_TIMER_SETTINGS);
        return new Observation(queue,TrainingCampBusyRead.fullClock(raw),y);
    }

    static boolean agrees(Observation a, Observation b){
        if(a==null || b==null || a.queue()!=b.queue() || Math.abs(a.headerY()-b.headerY())>3)return false;
        if(a.remaining()==null || b.remaining()==null)return a.remaining()==null && b.remaining()==null;
        long delta=a.remaining().minus(b.remaining()).abs().toSeconds();
        return delta<=25;
    }
}
