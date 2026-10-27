package io.github.devmrko.companion;
import io.github.devmrko.companion.adapter.JGitSpecifications;
import io.github.devmrko.companion.port.GitSpecifications.Target;
import io.github.devmrko.companion.domain.Catalog.Source;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GitSpecificationsTest {
    Target target(String url,String branch,String path){return new Target(new Source("id","GITHUB","source",url,"main",""),branch,path,"x-access-token","ocid1.vaultsecret.oc1.test");}
    @Test void onlyKnownHttpsGitHostsAndYamlPathsAreAllowed(){
        assertDoesNotThrow(()->JGitSpecifications.validate(target("https://github.com/org/repo.git","feature/build","ci/build_spec.yaml"),"ap-seoul-1"));
        for(String url:new String[]{"http://github.com/org/repo","https://user:token@github.com/org/repo","https://localhost/org/repo","https://github.com.evil.example/org/repo","https://github.com/org/repo?token=secret"})assertThrows(RuntimeException.class,()->JGitSpecifications.validate(target(url,"main","build_spec.yaml"),"ap-seoul-1"));
        for(String path:new String[]{"../build_spec.yaml",".git/config","dir/../build.yaml","build.sh"})assertThrows(RuntimeException.class,()->JGitSpecifications.validate(target("https://github.com/org/repo","main",path),"ap-seoul-1"));
        assertThrows(RuntimeException.class,()->JGitSpecifications.validate(target("https://github.com/org/repo","main:other","build.yaml"),"ap-seoul-1"));
    }
}
