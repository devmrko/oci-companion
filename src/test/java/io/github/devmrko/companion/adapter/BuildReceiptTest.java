package io.github.devmrko.companion.adapter;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
class BuildReceiptTest {
 @Test void onlyTheExactTestedArmExecutionCanRecover(){
  String id=UUID.randomUUID().toString(),commit="a".repeat(40);var json=JsonMapper.builder().build();var labels=new HashMap<>(BuildReceipt.labels(id,commit));
  var good=json.valueToTree(Map.of("architecture","arm64","os","linux","config",Map.of("Labels",labels)));assertTrue(BuildReceipt.matches(good,id,commit));
  assertFalse(BuildReceipt.matches(good,UUID.randomUUID().toString(),commit));assertFalse(BuildReceipt.matches(good,id,"b".repeat(40)));
  labels.remove("companion.build.tests");assertFalse(BuildReceipt.matches(json.valueToTree(Map.of("architecture","arm64","os","linux","config",Map.of("Labels",labels))),id,commit));
  assertFalse(BuildReceipt.matches(json.valueToTree(Map.of("architecture","amd64","os","linux","config",Map.of("Labels",BuildReceipt.labels(id,commit)))),id,commit));
 }
}
