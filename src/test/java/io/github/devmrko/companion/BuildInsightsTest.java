package io.github.devmrko.companion;
import io.github.devmrko.companion.service.BuildInsights;
import io.github.devmrko.companion.port.BuildHistory.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BuildInsightsTest {
 @Test void nativeTimesMissingInvalidAndUnfinishedAreNotZero(){
  assertNull(BuildInsights.seconds("",""));assertNull(BuildInsights.seconds("2026-11-01T00:01:00Z","2026-11-01T00:00:00Z"));
  var runs=List.of(new Run("a","a","SUCCEEDED","","2026-11-01T00:00:00Z","2026-11-01T00:01:00Z"),new Run("b","b","FAILED","","2026-11-01T00:00:00Z","2026-11-01T00:03:00Z"),new Run("c","c","CANCELED",""),new Run("d","d","IN_PROGRESS",""));
  var summary=BuildInsights.summarize(runs);assertEquals(2,summary.timed());assertEquals("2분 0초",summary.median());assertEquals(1,summary.failed());assertEquals(1,summary.canceled());assertEquals(1,summary.other());
 }
 @Test void windowUsesRequestTimeAndExcludesFutureAndMalformed(){
  var now=Instant.parse("2026-11-20T00:00:00Z");var runs=List.of(new Run("a","a","SUCCEEDED","2026-11-13T00:00:00Z"),new Run("b","b","FAILED","2026-11-12T23:59:59Z"),new Run("c","c","FAILED","2026-11-21T00:00:00Z"),new Run("d","d","FAILED","bad"));
  assertEquals(List.of("a"),BuildInsights.window(runs,now,7).stream().map(Run::id).toList());
 }
 @Test void stageIdentityPreventsComparingRecreatedStages(){
  var current=new Detail("a","a","SUCCEEDED","",List.of(new Step("test","SUCCEEDED","BUILD","new","2026-11-01T00:00:00Z","2026-11-01T00:01:00Z")));
  var previous=new Detail("b","b","SUCCEEDED","",List.of(new Step("test","SUCCEEDED","BUILD","old","2026-11-01T00:00:00Z","2026-11-01T00:02:00Z")));
  assertEquals(2,BuildInsights.stages(current,previous).size());assertEquals("비교 불가",BuildInsights.stages(current,previous).getFirst().difference());
 }
}
