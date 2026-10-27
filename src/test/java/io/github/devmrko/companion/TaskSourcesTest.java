package io.github.devmrko.companion;
import io.github.devmrko.companion.service.TaskSources;
import io.github.devmrko.companion.domain.Catalog.Source;
import io.github.devmrko.companion.port.BuildDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TaskSourcesTest {
    private BuildDefinition.Definition definition(List<BuildDefinition.Source> sources){return new BuildDefinition.Definition("p","project","ACTIVE","","r",List.of(new BuildDefinition.Stage("s","build","BUILD","ACTIVE",List.of(),"","",0,sources,List.of())));}
    @Test void exactRepositoryMatchDoesNotTrustDisplayNameOrEmbeddedClone() {
        var selected=new Source("registered","GITHUB","project","https://github.com/a/project.git","main","");
        var config=new BuildDefinition.Source("s::config","project","OCI","repo-config","https://devops.example/config","main",true);
        assertThrows(RuntimeException.class,()->TaskSources.match(selected,definition(List.of(config))));
        var correct=new BuildDefinition.Source("s::source","source","GitHub","","https://github.com/a/project","main",true);
        assertEquals(correct,TaskSources.match(selected,definition(List.of(correct))));
        assertThrows(RuntimeException.class,()->TaskSources.match(selected,definition(List.of(correct,correct))));
        assertFalse(TaskSources.sameUrl(selected.url(),"https://github.com/a/project-other"));
        assertFalse(TaskSources.sameUrl(selected.url(),"https://token@github.com/a/project.git"));
        assertFalse(TaskSources.sameUrl(selected.url(),"https://github.com/a/project?other=1"));
        var oci=new Source("registered","OCI","source","https://devops.example/config","main","repo-config");
        assertEquals(config,TaskSources.match(oci,definition(List.of(config))));
    }
}
