package io.github.devmrko.companion.adapter;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class FunctionDependencyCacheTest {
 @Test void runtimeHandoffIsBoundToRepositoryCommitArchitectureAndLock(){
  String repo="region.ocir.io/ns/builder",sha="sha256:"+"a".repeat(64),commit="b".repeat(40);
  var values=new HashMap<String,Object>(Map.of("runtimeImage",repo+"@"+sha,"runtimeTag",repo+":runtime-"+"c".repeat(64),"dependencyImage",repo+"@"+sha,"lockHash","d".repeat(64),"commit",commit,"environment",Map.of("arch","aarch64")));var json=JsonMapper.builder().build();
  assertDoesNotThrow(()->FunctionImageBuilds.validateRuntime(json.valueToTree(values),repo,commit));
  for(var entry:Map.of("runtimeImage","evil.ocir.io/ns/builder@"+sha,"runtimeTag",repo+":latest","lockHash","short","commit","e".repeat(40),"dependencyImage",repo+":latest").entrySet()){
   var changed=new HashMap<>(values);changed.put(entry.getKey(),entry.getValue());assertThrows(RuntimeException.class,()->FunctionImageBuilds.validateRuntime(json.valueToTree(changed),repo,commit));
  }
  values.put("environment",Map.of("arch","x86_64"));assertThrows(RuntimeException.class,()->FunctionImageBuilds.validateRuntime(json.valueToTree(values),repo,commit));
 }
}
