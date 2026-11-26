package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.domain.Models.Connection;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class BuildExecutionLocksTest {
 @TempDir Path root;
 @Test void separateInstancesExcludeSameBuilderButAllowDifferentBuilder(){
  var store=new JsonBuildSpecDraftStore(root.toString());var first=new BuildExecutionLocks(root.toString(),store);var second=new BuildExecutionLocks(root.toString(),store);
  try(var lease=first.acquire("physical-function","run-a",false)){
   assertThrows(IllegalStateException.class,()->second.acquire("physical-function","run-b",false));
   try(var other=second.acquire("different-function","run-c",false)){other.resolved();}
   lease.resolved();
  }
  try(var lease=second.acquire("physical-function","run-b",false)){lease.resolved();}
 }
 @Test void processLossRetainsUncertaintyAndOnlyMatchingRecoveryCanClearIt(){
  var store=new JsonBuildSpecDraftStore(root.toString());var first=new BuildExecutionLocks(root.toString(),store);
  try(var lost=first.acquire("physical-function","run-a",false)){}
  var restarted=new BuildExecutionLocks(root.toString(),store);
  assertThrows(IllegalStateException.class,()->restarted.acquire("physical-function","run-b",false));
  assertThrows(IllegalStateException.class,()->restarted.acquire("physical-function","wrong-run",true));
  try(var retry=restarted.acquire("physical-function","run-a",true)){}
  assertThrows(IllegalStateException.class,()->restarted.acquire("physical-function","run-b",false));
  try(var recovery=restarted.acquire("physical-function","run-a",true)){recovery.resolved();}
  try(var next=restarted.acquire("physical-function","run-b",false)){next.resolved();}
 }
 @Test void aliasesAndDifferentUserProfilesDoNotBypassPhysicalIdentity(){
  var a=new Connection("profile-a","tenancy","user-a","region","",false);var b=new Connection("profile-b","tenancy","user-b","region","",false);
  var one=new ArmBuilds.Builder("one","","","","env","ns","pull","PRIVATE_ENDPOINT","FUNCTION","ocid1.fnfunc.test");var alias=new ArmBuilds.Builder("two","","","","env","ns","pull","PRIVATE_ENDPOINT","FUNCTION","ocid1.fnfunc.test");
  assertEquals(BuildExecutionLocks.physical(a,"region",one),BuildExecutionLocks.physical(b,"region",alias));
 }
}
