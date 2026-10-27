package io.github.devmrko.companion;
import io.github.devmrko.companion.port.*;
import io.github.devmrko.companion.service.BuildChecks;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuildChecksTest {
    final BuildDefinition.Source source=new BuildDefinition.Source("source","source","OCI","repo","url","main",true);
    final BuildDefinition.Stage build=new BuildDefinition.Stage("build","build","빌드·테스트","ACTIVE",List.of("시작"),"image","build_spec.yaml",3600,List.of(source),List.of());
    final BuildDefinition.Stage delivery=new BuildDefinition.Stage("delivery","delivery","산출물 전달","ACTIVE",List.of("build"),"","",0,List.of(),List.of("jar → ref"));
    final Map<String,BuildDefinition.Specification> specs=Map.of("build",new BuildDefinition.Specification("version: 0.1","",List.of("jar · BINARY · target/app.jar"),List.of()));
    BuildDefinition.Definition definition(BuildDefinition.Stage... stages){return new BuildDefinition.Definition("p","project","ACTIVE","","revision",List.of(stages));}
    BuildSetup.Settings settings(String type){return new BuildSetup.Settings("etag","p","",List.of(),List.of(new BuildSetup.Artifact("ref","ref",type,"repo/path:v1")),List.of(),List.of(),List.of(new BuildSetup.Delivery("delivery","etag","delivery",List.of(new BuildSetup.Mapping("jar","ref")))));}
    @Test void storageIsRequiredForStoreAndExistingDeliveryCannotBeSkippedByPurpose() {
        assertDoesNotThrow(()->BuildChecks.validate(definition(build),null,specs,"test"));
        assertThrows(RuntimeException.class,()->BuildChecks.validate(definition(build),null,specs,"store"));
        assertThrows(RuntimeException.class,()->BuildChecks.validate(definition(build,delivery),settings("GENERIC_FILE"),specs,"test"));
        assertDoesNotThrow(()->BuildChecks.validate(definition(build,delivery),settings("GENERIC_FILE"),specs,"store"));
    }
    @Test void missingReferenceUnreadableSpecWrongTypeAndEmptyPipelineBlockExecution() {
        assertThrows(RuntimeException.class,()->BuildChecks.validate(definition(build,delivery),null,specs,"store"));
        assertThrows(RuntimeException.class,()->BuildChecks.validate(definition(build,delivery),settings("DOCKER_IMAGE"),specs,"store"));
        assertThrows(RuntimeException.class,()->BuildChecks.validate(definition(build),null,Map.of(),"test"));
        assertThrows(RuntimeException.class,()->BuildChecks.validate(definition(),null,Map.of(),"test"));
        assertThrows(RuntimeException.class,()->BuildChecks.validate(definition(build),null,specs,null));
    }
}
