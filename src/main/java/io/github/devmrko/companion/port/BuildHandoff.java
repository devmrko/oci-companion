package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
public interface BuildHandoff {
    record Proof(String buildRun,String fingerprint,int packages,String kind) {
        public Proof(String buildRun,String fingerprint,int packages){this(buildRun,fingerprint,packages,"FILE");}
        public String summary(){return "IMAGE".equals(kind)?"빌드 실행 기록과 배포 이미지 주소·digest 일치":"SHA-256·크기 일치";}
    }
    default java.util.List<String> images(Connection c,String region,String compartment,String buildPipeline,String buildRun){return java.util.List.of();}
    Proof verify(Connection c,String region,String compartment,String buildPipeline,String buildRun,String deployPipeline);
}
