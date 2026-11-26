package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.port.DeploymentImageVerification.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
class OkeImageVerificationTest {
 final JsonMapper json=JsonMapper.builder().build();final String wanted="sha256:"+"a".repeat(64),old="sha256:"+"b".repeat(64);
 final OkeWorkloadHealth.Target target=new OkeWorkloadHealth.Target("app","p","t","u","r","comp","cluster","/unused","ns","app",null,null);
 final Expected expected=new Expected("ns","app",2,Map.of("app","region.ocir.io/ns/app@"+wanted));
 String deployment(String image){return """
 {"kind":"Deployment","metadata":{"name":"app","namespace":"ns","uid":"dep","generation":2},"spec":{"replicas":2,"template":{"spec":{"containers":[{"name":"app","image":"region.ocir.io/ns/app@%s"}]}}},"status":{"observedGeneration":2,"replicas":2,"readyReplicas":2,"availableReplicas":2,"updatedReplicas":2}}
 """.formatted(image);}
 String sets="""
 {"items":[{"metadata":{"namespace":"ns","uid":"rs","ownerReferences":[{"kind":"Deployment","uid":"dep","controller":true}]}}]}
 """;
 String pod(String name,String owner,String digest,boolean ready){return """
 {"metadata":{"name":"%s","uid":"%s","namespace":"ns","ownerReferences":[{"kind":"ReplicaSet","uid":"%s","controller":true}]},"status":{"phase":"Running","conditions":[{"type":"Ready","status":"%s"}],"containerStatuses":[{"name":"app","imageID":"docker-pullable://region.ocir.io/ns/app@%s","ready":%s}]}}
 """.formatted(name,name,owner,ready?"True":"False",digest,ready);}
 Result verify(String d,String pods){return OkeWorkloadHealth.verifyImages(target,expected,json.readTree(d),json.readTree(sets),json.readTree("{\"items\":["+pods+"]}"));}
 @Test void oldArtifactReadyPodsFailEvenWhenOciSaysSucceeded(){var result=verify(deployment(old),pod("one","rs",old,true)+","+pod("two","rs",old,true));assertEquals("FAILED",result.state());assertEquals("실패: 이미지 불일치",result.detail());assertEquals(wanted,result.images().getFirst().expected());assertEquals(old,result.images().getFirst().actual());}
 @Test void podImageIdIsAuthoritativeNotJustDeploymentSpec(){assertEquals("FAILED",verify(deployment(wanted),pod("one","rs",old,true)+","+pod("two","rs",wanted,true)).state());}
 @Test void onlyMatchingReadyOwnedPodsPass(){var result=verify(deployment(wanted),pod("one","rs",wanted,true)+","+pod("two","rs",wanted,true)+","+pod("foreign","other",old,false));assertEquals("SUCCEEDED",result.state());assertEquals(2,result.images().size());}
 @Test void missingUnreadyOrUnobservedCannotPass(){assertEquals("FAILED",verify(deployment(wanted),"").state());assertEquals("FAILED",verify(deployment(wanted),pod("one","rs",wanted,false)+","+pod("two","rs",wanted,true)).state());assertEquals("FAILED",verify(deployment(wanted).replace("observedGeneration\":2","observedGeneration\":1"),pod("one","rs",wanted,true)+","+pod("two","rs",wanted,true)).state());}
 @Test void malformedImageIdCannotPass(){assertEquals("FAILED",verify(deployment(wanted),pod("one","rs","unknown",true)+","+pod("two","rs",wanted,true)).state());}
}
