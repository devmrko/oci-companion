package io.github.devmrko.companion.service;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.*;
import java.util.*;
import org.springframework.stereotype.Service;
@Service
public class DeploymentArchitectures {
    private final ImagePlatforms platforms;
    public DeploymentArchitectures(ImagePlatforms platforms){this.platforms=platforms;}
    public List<DeploymentImages.Check> inspect(Connection c,String region,DeploymentRuns.Plan plan){
        var results=new ArrayList<DeploymentImages.Check>();int count=0;
        for(String image:new LinkedHashSet<>(plan.images())){
            if(++count>20){results.add(new DeploymentImages.Check(image,DeploymentImages.State.UNKNOWN,"아키텍처 조회 한도 초과"));continue;}
            var result=platforms.read(c,region,image);
            if(result==null)result=ImagePlatforms.Result.unknown();
            results.add(compare(image,plan.imageArchitectures().getOrDefault(image,List.of()),result));
        }
        return List.copyOf(results);
    }
    static DeploymentImages.Check compare(String image,List<String> required,ImagePlatforms.Result actual){
        var supported=actual.platforms().stream().map(ImagePlatforms.Platform::architecture).collect(java.util.stream.Collectors.toSet());
        String detail="이미지: "+(actual.platforms().isEmpty()?"미확인":actual.platforms().stream().map(p->p.os()+"/"+p.architecture()).distinct().sorted().toList())+" · 명세 nodeSelector: "+required;
        if(actual.complete()&&required.stream().filter(a->Set.of("amd64","arm64").contains(a)).anyMatch(a->!supported.contains(a)))return new DeploymentImages.Check(image,DeploymentImages.State.BLOCKED,detail+" · 아키텍처 불일치. 이미지 또는 배포 아키텍처를 변경하세요.");
        if(required.isEmpty()||required.stream().anyMatch(a->!Set.of("amd64","arm64").contains(a))||!actual.complete())return new DeploymentImages.Check(image,DeploymentImages.State.UNKNOWN,detail+" · 명시적 nodeSelector 또는 이미지 플랫폼을 확인하지 못했습니다.");
        return new DeploymentImages.Check(image,DeploymentImages.State.VERIFIED,detail+" · 명세와 일치. 실제 노드 존재·OS·CPU 세부 기능·스케줄링은 별도 확인입니다.");
    }
}
