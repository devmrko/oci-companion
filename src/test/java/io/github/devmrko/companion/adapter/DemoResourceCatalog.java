package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.domain.Catalog.*;
import io.github.devmrko.companion.port.ResourceCatalog;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
@Component @Profile("demo")
public class DemoResourceCatalog implements ResourceCatalog {
    public List<Choice> regions(Connection c) { return List.of(new Choice("ap-seoul-1","ap-seoul-1"),new Choice("us-ashburn-1","us-ashburn-1")); }
    public List<Choice> compartments(Connection c) { return List.of(new Choice(c.tenancy(),"테넌시"),new Choice("demo-development","개발")); }
    public List<Resource> resources(Connection c,String region,String compartment) {
        return List.of(new Resource("demo-project","companion","PROJECT","ACTIVE",""),
            new Resource("demo-repository","companion-api","REPOSITORY","ACTIVE","https://example.com/demo/companion-api.git"),
            new Resource("demo-build","api-build","BUILD","ACTIVE",""),
            new Resource("demo-vm","vm-deploy","DEPLOY","ACTIVE",""));
    }
}
