package io.github.devmrko.companion.adapter;
import java.util.*;
import tools.jackson.databind.JsonNode;
/** Recovery accepts a unique execution's final tested image, never merely an existing tag. */
final class BuildReceipt {
 static Map<String,String> labels(String id,String commit){if(!id.matches("[a-f0-9-]{36}")||!commit.matches("[a-f0-9]{40}"))throw new IllegalArgumentException();return Map.of("companion.build.schema","1","companion.build.id",id,"companion.build.commit",commit,"companion.build.tests","passed");}
 static boolean matches(JsonNode config,String id,String commit){
  if(!"arm64".equals(config.path("architecture").asText())||!"linux".equals(config.path("os").asText()))return false;
  var labels=config.path("config").path("Labels");return labels(id,commit).entrySet().stream().allMatch(e->e.getValue().equals(labels.path(e.getKey()).asText()));
 }
}
