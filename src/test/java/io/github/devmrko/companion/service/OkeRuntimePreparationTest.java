package io.github.devmrko.companion.service;
import io.github.devmrko.companion.port.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OkeRuntimePreparationTest {
 OkeDeploymentSetup.Input input(String cm,String ingress,boolean tls){return new OkeDeploymentSetup.Input("env","repo","img","test","","ns","hello","amd64",1,250,256,8080,"/","","","","",true,null,cm,"","","","",30,"PUBLIC_ENDPOINT",ingress,tls);}
 OkeRuntimeResources.Snapshot snapshot(boolean available,String port,String secretType,String serviceType){
  var lists=new HashMap<String,OkeRuntimeResources.Listing>();
  lists.put("configmaps",new OkeRuntimeResources.Listing(available,List.of(new OkeRuntimeResources.Resource("settings","")),""));
  lists.put("secrets",new OkeRuntimeResources.Listing(true,List.of(new OkeRuntimeResources.Resource("tls",secretType)),""));
  lists.put("ingresses",new OkeRuntimeResources.Listing(true,List.of(new OkeRuntimeResources.Resource("route","")),""));
  lists.put("services",new OkeRuntimeResources.Listing(true,List.of(),""));
  return new OkeRuntimeResources.Snapshot(Instant.now(),"cluster","ns",lists,List.of(new OkeRuntimeResources.Ingress("route","nginx",List.of(new OkeRuntimeResources.Route("hello.example.com","/","hello",port)),List.of(new OkeRuntimeResources.Tls(List.of("hello.example.com"),"tls")),List.of())),List.of(new OkeRuntimeResources.Service("hello",serviceType,List.of())));
 }
 @Test void referencesAndNamedPortMatchButAddressRemainsWarning(){var r=DefaultOkeRuntimePreparation.evaluate(snapshot(true,"http","kubernetes.io/tls","ClusterIP"),input("settings","route",true));assertTrue(r.allowed());assertTrue(r.checks().stream().anyMatch(c->c.key().equals("endpoint")&&c.state()==OkeRuntimePreparation.State.WARN));}
 @Test void absentAndUnreadableAreDifferentFailures(){var missing=DefaultOkeRuntimePreparation.evaluate(snapshot(true,"8080","kubernetes.io/tls","ClusterIP"),input("missing","",false));assertEquals(OkeRuntimePreparation.State.FAIL,missing.checks().getFirst().state());var unavailable=DefaultOkeRuntimePreparation.evaluate(snapshot(false,"8080","kubernetes.io/tls","ClusterIP"),input("settings","",false));assertEquals(OkeRuntimePreparation.State.UNKNOWN,unavailable.checks().getFirst().state());assertFalse(unavailable.allowed());}
 @Test void wrongBackendSecretTypeAndExistingLoadBalancerBlock(){assertFalse(DefaultOkeRuntimePreparation.evaluate(snapshot(true,"9090","kubernetes.io/tls","ClusterIP"),input("","route",true)).allowed());assertFalse(DefaultOkeRuntimePreparation.evaluate(snapshot(true,"8080","Opaque","ClusterIP"),input("","route",true)).allowed());assertFalse(DefaultOkeRuntimePreparation.evaluate(snapshot(true,"8080","kubernetes.io/tls","LoadBalancer"),input("","route",true)).allowed());}
 @Test void tlsRequiresAnIngress(){assertFalse(DefaultOkeRuntimePreparation.evaluate(snapshot(true,"8080","kubernetes.io/tls","ClusterIP"),input("","",true)).allowed());}
 @Test void wildcardMatchesOnlyOneLabel(){assertTrue(DefaultOkeRuntimePreparation.covers("*.example.com","hello.example.com"));assertFalse(DefaultOkeRuntimePreparation.covers("*.example.com","a.b.example.com"));assertFalse(DefaultOkeRuntimePreparation.covers("*.example.com","example.com"));assertFalse(DefaultOkeRuntimePreparation.covers("*",""));}
 @Test void noOptionalReferencesDoesNotQueryCluster(){var resources=mock(OkeRuntimeResources.class);assertTrue(new DefaultOkeRuntimePreparation(resources).inspect(null,"region","compartment",input("","",false)).allowed());verifyNoInteractions(resources);}
 @Test void missingSecretFileVolumeBlocksDeployment(){
  var mapper=com.fasterxml.jackson.databind.json.JsonMapper.builder().build();var values=mapper.convertValue(input("","",false),Map.class);values.put("fileVolumes","secret wallet /opt/wallet true");
  var i=mapper.convertValue(values,OkeDeploymentSetup.Input.class);var report=DefaultOkeRuntimePreparation.evaluate(snapshot(true,"8080","Opaque","ClusterIP"),i);
  assertFalse(report.allowed());assertTrue(report.checks().stream().anyMatch(check->check.key().equals("secrets/wallet")&&check.state()==OkeRuntimePreparation.State.FAIL));
 }
}
