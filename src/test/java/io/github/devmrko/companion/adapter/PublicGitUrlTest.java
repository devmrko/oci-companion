package io.github.devmrko.companion.adapter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PublicGitUrlTest {
    @Test void onlyFixedPublicApiHostsCanBeRequested() {
        assertEquals("https://api.github.com/repos/devmrko/db-companion",OciSourceVersions.api("https://github.com/devmrko/db-companion.git"));
        assertEquals("https://gitlab.com/api/v4/projects/group%2Fsub%2Frepo/repository",OciSourceVersions.api("https://gitlab.com/group/sub/repo"));
        for(String bad:new String[]{"http://github.com/a/b","https://127.0.0.1/a/b","https://token@github.com/a/b","https://github.com/a/b?token=x","https://github.com/a/../b","https://github.com:444/a/b","https://github.com/a/b/tree/main","https://github.com.evil/a/b"})assertThrows(RuntimeException.class,()->OciSourceVersions.api(bad));
    }
}
