package io.github.devmrko.companion.adapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class BuildCacheJournalTest {
 @TempDir Path root;
 String hash(int n){return String.format("%064x",n);}
 @Test void ownershipAndRecentUsageAreRequiredAndLatestThreeLocksSurvive(){
  Instant now=Instant.parse("2026-11-26T00:00:00Z");var entries=new ArrayList<BuildCacheJournal.Entry>();
  for(int i=1;i<=5;i++)entries.add(new BuildCacheJournal.Entry("sha256:"+hash(i),"deps-"+hash(i),hash(i),"deps",now.minus(Duration.ofDays(90)).toString(),now.minus(Duration.ofDays(60-i)).toString(),true));
  entries.add(new BuildCacheJournal.Entry("sha256:"+hash(6),"deps-"+hash(6),hash(6),"deps",now.minus(Duration.ofDays(90)).toString(),now.minus(Duration.ofDays(80)).toString(),false));
  assertEquals(Set.of("sha256:"+hash(1),"sha256:"+hash(2)),BuildCacheJournal.eligible(new BuildCacheJournal.State(entries,false),now));
  var first=entries.removeFirst();entries.add(new BuildCacheJournal.Entry(first.digest(),first.tag(),first.lock(),first.kind(),first.createdAt(),now.toString(),true));
  assertFalse(BuildCacheJournal.eligible(new BuildCacheJournal.State(entries,false),now).contains(first.digest()));
 }
 @Test void observingExistingCacheDoesNotClaimOwnershipAndLaterUsePreservesCreation(){
  var store=new JsonBuildSpecDraftStore(root.toString());var locks=new BuildExecutionLocks(root.toString(),store);var journal=new BuildCacheJournal(store,locks);var t=Instant.parse("2026-01-01T00:00:00Z");
  var use=new BuildCacheJournal.Use("repository",hash(1),Map.of("deps","sha256:"+hash(2),"runtime","sha256:"+hash(3)),Map.of("deps","deps-"+hash(2),"runtime","runtime-"+hash(3)),Set.of("sha256:"+hash(2)));
  journal.record("scope",use,t);journal.record("scope",new BuildCacheJournal.Use(use.repository(),use.lock(),use.images(),use.tags(),Set.of()),t.plusSeconds(300));
  assertTrue(journal.read("scope").entries().stream().filter(e->e.kind().equals("deps")).findFirst().orElseThrow().owned());
  assertFalse(journal.read("scope").entries().stream().filter(e->e.kind().equals("runtime")).findFirst().orElseThrow().owned());
  assertTrue(journal.read("scope").entries().stream().allMatch(e->e.createdAt().equals(t.toString())&&e.lastUsed().equals(t.plusSeconds(300).toString())));
 }
 @Test void maintenanceExcludesNewBuildsAndUnresolvedBuildsExcludeMaintenance(){
  var store=new JsonBuildSpecDraftStore(root.toString());var locks=new BuildExecutionLocks(root.toString(),store);
  try(var maintenance=locks.maintenance()){assertThrows(IllegalStateException.class,()->locks.acquire("function","run",false));}
  try(var build=locks.acquire("function","run",false)){assertThrows(IllegalStateException.class,locks::maintenance);}
  assertThrows(IllegalStateException.class,locks::maintenance);
  try(var recover=locks.acquire("function","run",true)){recover.resolved();}
  try(var maintenance=locks.maintenance()){}
 }
 @Test void configDigestAndTagReferencesAreBothProtected(){var digests=new HashSet<String>();var tags=new HashSet<String>();OciBuildCacheMaintenance.protect(Map.of("RUNTIME_BASE_IMAGE","yny.ocir.io/ns/repo@sha256:"+hash(1),"IMAGE_BASE","ap-chuncheon-1.ocir.io/ns/repo:deps-"+hash(2)),"ns/repo",digests,tags);assertEquals(Set.of("sha256:"+hash(1)),digests);assertEquals(Set.of("deps-"+hash(2)),tags);}
 @Test void uncertainRecoveredUseDefersDeletionWithoutBlockingNewBuilds(){var store=new JsonBuildSpecDraftStore(root.toString());var locks=new BuildExecutionLocks(root.toString(),store);locks.uncertainCacheUse();assertThrows(IllegalStateException.class,locks::maintenance);try(var build=locks.acquire("function","run",false)){build.resolved();}}

}
