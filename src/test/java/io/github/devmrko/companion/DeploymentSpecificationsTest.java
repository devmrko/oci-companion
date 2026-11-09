package io.github.devmrko.companion;
import io.github.devmrko.companion.port.DeploymentSetup.Input;
import io.github.devmrko.companion.service.DeploymentSpecifications;
import io.github.devmrko.foundation.core.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class DeploymentSpecificationsTest {
    Input input(String destination,String variables,String vault){return new Input("env","repo","artifact","release","description",destination,"ocarun","systemctl stop app","cp app /opt/app/","systemctl start app","curl --fail localhost:8080/health","echo recover",variables,vault,600,1);}
    @Test void nativeSpecPreservesCommandsAndVaultReferences(){
        Map<String,Object> spec=new Yaml().load(DeploymentSpecifications.render(input("/var/ocarun_staging/app","PORT=8080\nVALUE=a=b","TOKEN=ocid1.vaultsecret.oc1.example")));
        assertEquals("deployment",spec.get("component"));var env=(Map<?,?>)spec.get("env");var vars=(Map<?,?>)env.get("variables");assertEquals("a=b",vars.get("VALUE"));assertEquals("/var/ocarun_staging/app",vars.get("DEPLOY_DIR"));assertEquals("ocid1.vaultsecret.oc1.example",((Map<?,?>)env.get("vaultVariables")).get("TOKEN"));var steps=(List<Map<String,Object>>)spec.get("steps");assertEquals(4,steps.size());assertEquals("set -e\ncurl --fail localhost:8080/health",steps.getLast().get("command"));assertNotNull(steps.getFirst().get("onFailure"));
    }
    @Test void rejectsUnsafeOrConflictingInputs(){
        for(String path:List.of("/","relative","/tmp/../etc","/tmp/a b"))assertThrows(BusinessException.class,()->DeploymentSpecifications.render(input(path,"","")));
        for(String vars:List.of("PORT=1\nPORT=2","DEPLOY_DIR=/tmp","OCI_DEVOPS_X=y","not-a-key=x"))assertThrows(BusinessException.class,()->DeploymentSpecifications.render(input("/tmp/app",vars,"")));
        assertThrows(BusinessException.class,()->DeploymentSpecifications.render(input("/tmp/app","TOKEN=value","TOKEN=ocid1.vaultsecret.oc1.x")));
        assertThrows(BusinessException.class,()->DeploymentSpecifications.render(input("/tmp/app","","TOKEN=plaintext")));
        assertThrows(BusinessException.class,()->DeploymentSpecifications.render(null));
    }
}
