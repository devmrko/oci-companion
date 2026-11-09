package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.List;
public interface DeploymentImages {
    enum State { VERIFIED, BLOCKED, UNKNOWN }
    record Check(String image,State state,String note) {
        public String label(){return switch(state){case VERIFIED -> "확인";case BLOCKED -> "실행 불가";case UNKNOWN -> "미확인";};}
    }
    List<Check> inspect(Connection connection,String region,String compartment,List<String> images);
}
