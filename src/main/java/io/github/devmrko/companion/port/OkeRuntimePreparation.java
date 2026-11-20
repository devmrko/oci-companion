package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import java.time.Instant;
import java.util.List;
public interface OkeRuntimePreparation {
 enum State { PASS, FAIL, UNKNOWN, WARN }
 record Check(String key,State state,String note) {}
 record Report(Instant observedAt,List<Check> checks) {
  public boolean allowed(){return checks.stream().noneMatch(c->c.state()==State.FAIL||c.state()==State.UNKNOWN);}
 }
 Report inspect(Connection connection,String region,String compartment,OkeDeploymentSetup.Input input);
}
