package dev.frostguard.tasks.alliance;

import dev.frostguard.api.domain.RawImageData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AlliancePageExitTest {
 @BeforeAll static void cv()throws Exception{OpenCvPatternLocator.loadNativeLibrary();}
 @Test void liveScreensAreClassifiedWithoutGenericOverlaySearch()throws Exception{
  assertEquals(AlliancePageExit.Page.TECH,AlliancePageExit.page(frame("alliance-tech-live-20261115.png")));
  assertEquals(AlliancePageExit.Page.ALLIANCE,AlliancePageExit.page(frame("alliance-live-20261115.png")));
  assertEquals(AlliancePageExit.Page.CITY,AlliancePageExit.page(frame("home-city-anchor-redacted.png")));
 }
 @Test void atMostTwoVerifiedBackTapsAndCityRequired()throws Exception{
  var tech=frame("alliance-tech-live-20261115.png");
  var alliance=frame("alliance-live-20261115.png");
  var city=frame("home-city-anchor-redacted.png");
  var sequence=List.of(tech,tech,alliance,alliance,city);
  var n=new AtomicInteger();var clicks=new AtomicInteger();
  assertEquals(AlliancePageExit.Result.HOME,AlliancePageExit.returnHome(
   ()->sequence.get(n.getAndIncrement()),p->{assertEquals(43,p.getX());clicks.incrementAndGet();},()->{}));
  assertEquals(2,clicks.get());assertEquals(5,n.get());
 }
 @Test void cannotBlindlyTapWhenTitleChanges()throws Exception{
  var tech=frame("alliance-tech-live-20261115.png");
  var city=frame("home-city-anchor-redacted.png");
  var n=new AtomicInteger();var clicks=new AtomicInteger();
  assertEquals(AlliancePageExit.Result.UNVERIFIED,AlliancePageExit.returnHome(
   ()->n.getAndIncrement()==0?tech:city,p->clicks.incrementAndGet(),()->{}));
  assertEquals(0,clicks.get());
 }
 private static RawImageData frame(String filename)throws Exception{
  BufferedImage image;
  try(var stream=Objects.requireNonNull(AlliancePageExitTest.class.getResourceAsStream("/initialize/"+filename))){image=ImageIO.read(stream);}
  byte[] b=new byte[720*1280*4];
  for(int y=0;y<1280;y++)for(int x=0;x<720;x++){
   int c=image.getRGB(x,y),o=(y*720+x)*4;b[o]=(byte)(c>>16);b[o+1]=(byte)(c>>8);b[o+2]=(byte)c;b[o+3]=(byte)255;
  }
  return RawImageData.capture(b,720,1280,32);
 }
}
