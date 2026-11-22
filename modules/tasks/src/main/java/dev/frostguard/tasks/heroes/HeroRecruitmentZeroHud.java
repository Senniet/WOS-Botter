package dev.frostguard.tasks.heroes;

import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.convert.ImageConverter;

import java.awt.image.BufferedImage;

/**
 * Strict 2026 Serve-Emu top-bar zero digit fallback. Tesseract regularly
 * returns an empty string for the thin white ring "0" on the blue key HUD,
 * although it reads the filled "2"/"4" glyphs on the same layout correctly.
 *
 * This detector does NOT infer any positive key count and cannot authorize
 * spending. It only identifies a single, well-formed zero digit in the
 * already-confirmed, dedicated owned-key counter slot.
 */
final class HeroRecruitmentZeroHud {
    private HeroRecruitmentZeroHud() {}

    static boolean isZero(RawImageData frame, PointData corner1, PointData corner2) {
        if (frame == null || !frame.isValid()
                || frame.getWidth() != 720 || frame.getHeight() != 1280
                || corner1 == null || corner2 == null) return false;
        int x0 = corner1.getX(), x1 = corner2.getX();
        int y0 = corner1.getY() + 4, y1 = corner2.getY() - 10;
        if (x0 < 0 || x1 > 720 || y0 < 0 || y1 > 1280
                || x1 - x0 < 28 || x1 - x0 > 55 || y1 <= y0) return false;
        BufferedImage image = ImageConverter.toBufferedImage(frame);
        int minX=x1,minY=y1,maxX=-1,maxY=-1,found=0;
        for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++) {
            if(bright(image,x,y)) {
                found++;minX=Math.min(minX,x);minY=Math.min(minY,y);
                maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);
            }
        }
        if(found<20 || maxX<0 || maxY<0) return false;
        int w=maxX-minX+1,h=maxY-minY+1;
        if(w<10 || w>16 || h<13 || h>18) return false;
        int centerX=(minX+maxX)/2;
        if(Math.abs(centerX-(x0+x1)/2)>7
                || Math.abs((minY+maxY)/2-(corner1.getY()+17))>5)
            return false;
        int top=0,bottom=0;
        for(int x=centerX-2;x<=centerX+2;x++) {
            for(int y=minY+1;y<minY+4;y++)if(bright(image,x,y))top++;
            for(int y=maxY-3;y<maxY;y++)if(bright(image,x,y))bottom++;
        }
        if(top<7 || bottom<7)return false;
        int ringRows=0;
        for(int y=minY+5;y<=maxY-5;y++) {
            boolean left=false,right=false,centerClear=true;
            for(int x=minX;x<=minX+4;x++)left|=bright(image,x,y);
            for(int x=maxX-4;x<=maxX;x++)right|=bright(image,x,y);
            for(int x=centerX-2;x<=centerX+2;x++)centerClear&=!bright(image,x,y);
            if(left&&right&&centerClear)ringRows++;
        }
        return ringRows>=4;
    }

    private static boolean bright(BufferedImage image,int x,int y) {
        int rgb=image.getRGB(x,y);
        return (rgb>>>16&255)>=213 && (rgb>>>8&255)>=213 && (rgb&255)>=213;
    }
}
