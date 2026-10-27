package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.port.GitCredentials;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.service.Errors;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
@Component
public class ConfiguredGitCredentials implements GitCredentials {
    private final String path;private final Set<String> hosts;
    public ConfiguredGitCredentials(@Value("${companion.oci-config}") String path,@Value("${companion.git-hosts:}") String hosts){this.path=path;this.hosts=new HashSet<>(Arrays.asList(hosts.split(",")));}
    public boolean allowedHost(String host){return host!=null&&hosts.contains(host);}
    public Authentication configured(String host){
        if(!allowedHost(host))throw Errors.GIT_ACCESS.exception();
        for(var key:System.getenv().keySet())if(key.matches("COMPANION_GIT_[A-Z0-9_]+_HOST")&&host.equals(System.getenv(key))){String prefix=key.substring(0,key.length()-5);String user=System.getenv(prefix+"_USER"),password=System.getenv(prefix+"_TOKEN");if(user!=null&&password!=null)return new Authentication(user,password);}
        throw Errors.GIT_ACCESS.exception();
    }
    public String secret(Connection c,String region,String host,String reference){
        if(reference.startsWith("env:")) {
            if(!reference.matches("env:[A-Z][A-Z0-9_]{0,40}")||!allowedHost(host))throw Errors.GIT_ACCESS.exception();
            String prefix="COMPANION_GIT_"+reference.substring(4);
            if(!host.equals(System.getenv(prefix+"_HOST")))throw Errors.GIT_ACCESS.exception();
            String value=System.getenv(prefix+"_TOKEN");if(value==null||value.isBlank())throw Errors.GIT_ACCESS.exception();return value;
        }
        if(!reference.matches("ocid1\\.vaultsecret\\.[A-Za-z0-9.]+"))throw Errors.INVALID.exception();
        try(var client=com.oracle.bmc.secrets.SecretsClient.builder().build(new OciBuildHistory(path).authentication(c))) {
            client.setRegion(region);
            var bundle=client.getSecretBundle(com.oracle.bmc.secrets.requests.GetSecretBundleRequest.builder().secretId(reference).stage(com.oracle.bmc.secrets.requests.GetSecretBundleRequest.Stage.Current).build()).getSecretBundle();
            var content=(com.oracle.bmc.secrets.model.Base64SecretBundleContentDetails)bundle.getSecretBundleContent();
            return new String(Base64.getDecoder().decode(content.getContent()),StandardCharsets.UTF_8).strip();
        }catch(Exception e){throw Errors.GIT_ACCESS.exception();}
    }
}
