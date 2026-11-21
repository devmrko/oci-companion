package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.port.ObservabilitySetup.Settings;
import io.github.devmrko.companion.domain.Models.Connection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;
class NativeObservabilitySetupTest {
 @TempDir Path root;
 @Test void rejectsActiveLinksAndEmbeddedCredentials(){
  for(String url:new String[]{"javascript:alert(1)","https://user:token@host","https://host?token=secret","https://host/#secret","file:///tmp/x"})
   assertThatThrownBy(()->NativeObservabilitySetup.validate(new Settings("grafana",url,"https://collector:4318","api","java"))).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void configurationIsScopedAndSurvivesOtherEdits()throws Exception{
  var registry=new AgentConnectionRegistry(root.resolve("apps.json").toString(),root.resolve("creds.json").toString());
  var c=new Connection("DEFAULT","tenancy","user","region","test",false);
  var dep=new AgentConnectionRegistry.Dependency("OKE","cluster","ocid1.cluster.test");
  var id=registry.registerOke(c,"region","comp","","app","test",dep);
  var setup=new NativeObservabilitySetup(registry);
  var value=new Settings("pinpoint","https://apm.example.com","https://collector.example.com:4318","api","python");
  setup.save(c,"region","comp",id,value);
  registry.registerOke(c,"region","comp",id,"renamed","test",dep);
  assertThat(setup.get(c,"region","comp",id)).isEqualTo(value);
  assertThatThrownBy(()->setup.get(c,"region","other",id)).isInstanceOf(IllegalArgumentException.class);
  assertThat(setup.instrumentation(value)).contains("OTEL_METRICS_EXPORTER=none","opentelemetry-instrument").doesNotContain("javaagent");
 }
}
