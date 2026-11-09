package io.github.devmrko.companion.adapter;
import com.oracle.bmc.Region;
import com.oracle.bmc.artifacts.ArtifactsClient;
import com.oracle.bmc.artifacts.requests.*;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.DeploymentImages;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class OciDeploymentImages implements DeploymentImages {
    private final OciDeploymentSetup registry;
    public OciDeploymentImages(OciDeploymentSetup registry){this.registry=registry;}
    record Reference(String namespace,String repository,String digest) {}
    static Reference parse(String region,String image){
        var match=java.util.regex.Pattern.compile("^([^/]+)/([a-z0-9]+)/([a-z0-9]+(?:[._/-][a-z0-9]+)*)@(sha256:[a-f0-9]{64})$").matcher(image);
        if(!match.matches())return null;
        String code=Region.fromRegionId(region).getRegionCode();
        if(!Set.of(code+".ocir.io","ocir."+region+".oci.oraclecloud.com").contains(match.group(1)))return null;
        return new Reference(match.group(2),match.group(3),match.group(4));
    }
    public List<Check> inspect(Connection c,String region,String compartment,List<String> images){
        var result=new ArrayList<Check>();int count=0;
        for(String image:new LinkedHashSet<>(images)){
            if(c.demo()||++count>20){result.add(new Check(image,State.UNKNOWN,"조회 범위 제한 · 개별 저장소에서 확인하세요."));continue;}
            Reference ref;
            try{ref=parse(region,image);}catch(Exception e){ref=null;}
            if(ref==null){result.add(new Check(image,State.UNKNOWN,"현재 리전 OCIR의 digest 주소가 아닙니다. 해당 Registry에서 확인하세요."));continue;}
            try(var client=registry.artifacts(c,region)){result.add(inspect(client,compartment,image,ref));}
            catch(Exception e){result.add(new Check(image,State.UNKNOWN,"OCIR 조회 실패 · repos 읽기 권한·연결·선택 구획을 확인하세요."));}
        }
        return List.copyOf(result);
    }
    Check inspect(ArtifactsClient client,String comp,String image,Reference ref){
        // Only an exactly matched, readable repository allows a missing-digest conclusion.
        String page=null;
        for(int n=0;n<10;n++){
            var response=client.listContainerRepositories(ListContainerRepositoriesRequest.builder().compartmentId(comp).displayName(ref.repository()).limit(100).page(page).build());
            for(var summary:response.getContainerRepositoryCollection().getItems()){
                if(!ref.repository().equals(summary.getDisplayName())||!comp.equals(summary.getCompartmentId()))continue;
                var repo=client.getContainerRepository(GetContainerRepositoryRequest.builder().repositoryId(summary.getId()).build()).getContainerRepository();
                if(!comp.equals(repo.getCompartmentId())||!ref.namespace().equals(repo.getNamespace())||!ref.repository().equals(repo.getDisplayName()))continue;
                if(repo.getLifecycleState()==null)return new Check(image,State.UNKNOWN,"저장소 상태를 확인하지 못했습니다.");
                if(!"AVAILABLE".equals(repo.getLifecycleState().getValue()))return new Check(image,State.UNKNOWN,"저장소가 AVAILABLE 상태가 아닙니다. Registry에서 상태를 확인하세요.");
                return digest(client,comp,image,ref,repo.getId());
            }
            page=response.getOpcNextPage();if(page==null||page.isBlank())break;
        }
        return new Check(image,State.UNKNOWN,"현재 구획에서 동일 namespace·저장소를 확인하지 못했습니다. 다른 구획·권한을 확인하세요.");
    }
    private Check digest(ArtifactsClient client,String comp,String image,Reference ref,String repository){
        String page=null;
        for(int n=0;n<10;n++){
            var response=client.listContainerImages(ListContainerImagesRequest.builder().compartmentId(comp).repositoryId(repository).imageDigest(ref.digest()).limit(100).page(page).build());
            for(var item:response.getContainerImageCollection().getItems()){
                if(!comp.equals(item.getCompartmentId())||!repository.equals(item.getRepositoryId())||!ref.digest().equals(item.getDigest()))continue;
                if(item.getLifecycleState()==null)return new Check(image,State.UNKNOWN,"이미지 상태를 확인하지 못했습니다.");
                return switch(item.getLifecycleState().getValue()){
                    case "AVAILABLE" -> new Check(image,State.VERIFIED,"동일 저장소·digest AVAILABLE · 실행 주체의 pull 권한은 별도입니다.");
                    case "DELETED","DELETING" -> new Check(image,State.BLOCKED,"지정 digest가 삭제 중이거나 삭제됐습니다. 배포 이미지를 다시 선택하세요.");
                    default -> new Check(image,State.UNKNOWN,"이미지 상태를 확인하지 못했습니다.");
                };
            }
            page=response.getOpcNextPage();if(page==null||page.isBlank())return new Check(image,State.BLOCKED,"조회한 저장소에 지정 digest가 없습니다. 배포 이미지를 다시 선택하세요.");
        }
        return new Check(image,State.UNKNOWN,"이미지 조회 페이지 제한 · Registry에서 digest를 확인하세요.");
    }
}
