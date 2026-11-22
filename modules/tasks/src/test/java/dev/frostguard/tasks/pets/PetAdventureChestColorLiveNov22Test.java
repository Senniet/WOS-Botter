package dev.frostguard.tasks.pets;

import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.vision.match.OpenCvPatternLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PetAdventureChestColorLiveNov22Test {
 @BeforeAll static void openCv() throws Exception { OpenCvPatternLocator.loadNativeLibrary(); }
 @Test void detectsColorVariantsAtFlexiblePositionsAndSkipsOccupiedPin() throws Exception {
  byte[] map;
  try(var stream=Objects.requireNonNull(getClass().getResourceAsStream(
     "/pets/live-20261122/map-after-start.png"))) { map=stream.readAllBytes(); }
  var all=new ArrayList<ImageSearchResultData>();
  var timers=OpenCvPatternLocator.locateAllPatterns(map,TemplatesEnum.PETS_CHEST_ADVENTURE_TIMER,
      new PointData(0,0),new PointData(720,1280),80,5).stream()
      .filter(ImageSearchResultData::isFound).toList();
  for(var color:List.of(TemplatesEnum.PETS_CHEST_RED,TemplatesEnum.PETS_CHEST_PURPLE,TemplatesEnum.PETS_CHEST_BLUE)) {
   var matches=OpenCvPatternLocator.locateAllPatterns(map,color,
       new PointData(0,0),new PointData(720,1280),90,5).stream()
       .filter(ImageSearchResultData::isFound).toList();
   System.out.println(color+" detected "+matches);
   all.addAll(matches);
  }
  System.out.println("Timers detected "+timers);
  assertTrue(PetAdventureDecisions.occupiedByTimer(new PointData(222,308),timers));
  assertTrue(all.stream().anyMatch(hit->Math.abs(hit.getHitX()-630)<80
       && Math.abs(hit.getHitY()-820)<90),"Right idle chest");
  assertTrue(all.stream().anyMatch(hit->Math.abs(hit.getHitX()-285)<80
       && Math.abs(hit.getHitY()-1050)<90),"Bottom idle chest");
 }
}
