package io.github.devmrko.companion.service;
import io.github.devmrko.companion.port.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DeploymentArchitecturesTest {
    ImagePlatforms.Result image(boolean complete,String... arch){return new ImagePlatforms.Result(java.util.Arrays.stream(arch).map(a->new ImagePlatforms.Platform("linux",a)).toList(),complete,"");}
    @Test void mismatchBlocksOnlyWithCompleteImageAndExplicitSupportedSelector(){
        assertEquals(DeploymentImages.State.BLOCKED,DeploymentArchitectures.compare("image",List.of("arm64"),image(true,"amd64")).state());
        assertEquals(DeploymentImages.State.UNKNOWN,DeploymentArchitectures.compare("image",List.of("arm64"),image(false,"amd64")).state());
        assertEquals(DeploymentImages.State.UNKNOWN,DeploymentArchitectures.compare("image",List.of(""),image(true,"amd64")).state());
        assertEquals(DeploymentImages.State.UNKNOWN,DeploymentArchitectures.compare("image",List.of(),image(true,"amd64")).state());
        assertEquals(DeploymentImages.State.VERIFIED,DeploymentArchitectures.compare("image",List.of("arm64","amd64"),image(true,"amd64","arm64")).state());
        assertEquals(DeploymentImages.State.BLOCKED,DeploymentArchitectures.compare("image",List.of("amd64","arm64"),image(true,"amd64")).state());
    }
}
