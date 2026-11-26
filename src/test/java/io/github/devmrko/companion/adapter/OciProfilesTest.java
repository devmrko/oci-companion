package io.github.devmrko.companion.adapter;

import static org.junit.jupiter.api.Assertions.*;

import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.auth.RegionProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OciProfilesTest {
    static final String TENANCY="ocid1.tenancy.oc1..aaaaexampletenancy";
    static final String USER="ocid1.user.oc1..aaaaexampleuser";

    static String jwt(String sub) {
        var enc=Base64.getUrlEncoder().withoutPadding();
        return enc.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8))+"."
            +enc.encodeToString(("{\"sub\":\""+sub+"\",\"tenant\":\"t\"}").getBytes(StandardCharsets.UTF_8))+".c2ln";
    }

    static Path key(Path dir) throws Exception {
        var pair=KeyPairGenerator.getInstance("RSA");pair.initialize(2048);
        var der=pair.generateKeyPair().getPrivate().getEncoded();
        var pem="-----BEGIN PRIVATE KEY-----\n"+Base64.getMimeEncoder(64,"\n".getBytes()).encodeToString(der)+"\n-----END PRIVATE KEY-----\n";
        return Files.writeString(dir.resolve("key.pem"),pem);
    }

    @Test
    void sessionProfileSignsWithTheCurrentTokenAndTakesTheUserFromIt(@TempDir Path dir) throws Exception {
        key(dir);
        var token=Files.writeString(dir.resolve("token"),jwt(USER)+"\n");
        var config=Files.writeString(dir.resolve("config"),"""
            [SESSION]
            fingerprint=aa:bb
            key_file=key.pem
            tenancy=%s
            region=ap-chuncheon-1
            security_token_file=token
            """.formatted(TENANCY));
        var auth=OciProfiles.load(config.toString(),"SESSION");
        assertInstanceOf(OciProfiles.SessionProfile.class,auth);
        assertEquals(TENANCY,auth.getTenantId());
        assertEquals(USER,auth.getUserId());
        assertEquals("ap-chuncheon-1",((RegionProvider)auth).getRegion().getRegionId());
        assertEquals("ST$"+jwt(USER),auth.getKeyId());
        assertTrue(new String(auth.getPrivateKey().readAllBytes()).contains("PRIVATE KEY"));
        assertEquals("security_token",OciProfiles.cliAuth(config.toString(),"SESSION"));

        Files.writeString(token,jwt("ocid1.user.oc1..aaaarefreshed"));   // oci session refresh rewrites the file
        assertEquals("ST$"+jwt("ocid1.user.oc1..aaaarefreshed"),auth.getKeyId());
    }

    @Test
    void sessionProfileRejectsATokenWithoutAUserSubject(@TempDir Path dir) throws Exception {
        key(dir);
        Files.writeString(dir.resolve("token"),jwt("not-a-user"));
        var config=Files.writeString(dir.resolve("config"),"""
            [SESSION]
            fingerprint=aa:bb
            key_file=key.pem
            tenancy=%s
            region=ap-chuncheon-1
            security_token_file=token
            """.formatted(TENANCY));
        assertThrows(java.io.IOException.class,()->OciProfiles.load(config.toString(),"SESSION"));
        Files.writeString(dir.resolve("token"),"one.two");
        assertThrows(java.io.IOException.class,()->OciProfiles.load(config.toString(),"SESSION"));
    }

    @Test
    void apiKeyProfileStaysOnTheSdkProvider(@TempDir Path dir) throws Exception {
        var keyFile=key(dir);
        var config=Files.writeString(dir.resolve("config"),"""
            [API]
            user=%s
            fingerprint=aa:bb
            key_file=%s
            tenancy=%s
            region=ap-seoul-1
            """.formatted(USER,keyFile,TENANCY));
        var auth=OciProfiles.load(config.toString(),"API");
        assertInstanceOf(ConfigFileAuthenticationDetailsProvider.class,auth);
        assertEquals(USER,auth.getUserId());
        assertEquals("api_key",OciProfiles.cliAuth(config.toString(),"API"));
    }
}
