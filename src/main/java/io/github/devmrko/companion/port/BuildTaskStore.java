package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.List;
public interface BuildTaskStore {
    record Task(String id,String name,String runner,String application,String function,String pipeline,String source,String commit,String goal,String profiles,String directory,String artifact,String sourceId,String branch,String target,String architecture) {
        public Task { target=target==null?"UNSPECIFIED":target.equals("VM")?"FILE":target;architecture=architecture==null?"UNKNOWN":architecture;sourceId=sourceId==null?"":sourceId;branch=branch==null?"":branch; }
        public Task(String id,String name,String runner,String application,String function,String pipeline,String source,String commit,String goal,String profiles,String directory,String artifact,String sourceId,String branch){this(id,name,runner,application,function,pipeline,source,commit,goal,profiles,directory,artifact,sourceId,branch,"UNSPECIFIED","UNKNOWN");}
        public String targetLabel(){return switch(target){case "FILE" -> "파일 (JAR·압축 파일 등)";case "CONTAINER" -> "컨테이너 이미지";case "FUNCTION" -> "Function 패키지·이미지";default -> "유형 미지정";};}
        public String deploymentHint(){return switch(target){
            case "FILE" -> "VM: 런타임·의존성·실행 명령 확인. 컨테이너 배포는 이미지로 패키징 필요.";
            case "CONTAINER" -> "VM(Docker·Podman) / OKE / Container Instances: 이미지 아키텍처·포트·환경 변수 확인.";
            case "FUNCTION" -> "OCI Functions: 패키징 규격·런타임·애플리케이션 아키텍처 확인.";
            default -> "배포 대상은 산출물 생성 후 선택합니다.";
        };}
        public String architectureLabel(){return switch(architecture){case "X86_64" -> "x86 (amd64)";case "ARM64" -> "ARM (arm64)";default -> "아키텍처 미지정";};}
        public Task(String id,String name,String runner,String application,String function,String pipeline,String source,String commit,String goal,String profiles,String directory,String artifact){this(id,name,runner,application,function,pipeline,source,commit,goal,profiles,directory,artifact,"","");}
    }
    List<Task> list(Connection c,String region,String compartment);
    void save(Connection c,String region,String compartment,Task task);
    void delete(Connection c,String region,String compartment,String id);
}
