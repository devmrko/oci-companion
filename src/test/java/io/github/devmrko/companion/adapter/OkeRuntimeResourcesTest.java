package io.github.devmrko.companion.adapter;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
class OkeRuntimeResourcesTest {
 @Test void metadataIsScopedSortedAndBounded(){var r=OciOkeRuntimeResources.metadata("ns\tz\tOpaque\nns\ta\t\n","ns");assertEquals("a",r.items().getFirst().name());assertEquals("Opaque",r.items().getLast().type());assertThrows(IllegalArgumentException.class,()->OciOkeRuntimeResources.metadata("other\tx\tOpaque\n","ns"));assertThrows(IllegalArgumentException.class,()->OciOkeRuntimeResources.metadata("ns\tx\tOpaque\n".repeat(501),"ns"));assertTrue(OciOkeRuntimeResources.metadata("","ns").items().isEmpty());}
 @Test void ingressExtractsOnlyConnectionFields(){var node=JsonMapper.builder().build().readTree("""
 {"metadata":{"name":"web","annotations":{"ignored":"secret"}},"spec":{"ingressClassName":"nginx","rules":[{"host":"hello.example.com","http":{"paths":[{"path":"/","backend":{"service":{"name":"hello","port":{"number":8080}}}}]}}],"tls":[{"hosts":["hello.example.com"],"secretName":"tls"}]},"status":{"loadBalancer":{"ingress":[{"hostname":"edge.example.com"}]}}}
 """);var r=OciOkeRuntimeResources.ingress(node);assertEquals("8080",r.routes().getFirst().port());assertEquals("tls",r.tls().getFirst().secret());assertEquals("edge.example.com",r.addresses().getFirst());assertFalse(r.toString().contains("ignored"));}
 @Test void legacyIngressClassAnnotationIsShownWithoutClusterScopeLookup(){var mapper=JsonMapper.builder().build();var node=mapper.readTree("{\"metadata\":{\"name\":\"web\",\"annotations\":{\"kubernetes.io/ingress.class\":\"isolated-edge\"}},\"spec\":{}}");assertEquals("isolated-edge",OciOkeRuntimeResources.ingress(node).controller());}

}
