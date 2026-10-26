package io.github.devmrko.companion.service;
import java.util.*;
public final class PermissionHelp {
    private PermissionHelp() {}
    public record Rule(String task,String policy) {}
    public static List<Rule> rules(String menu,String compartment) {
        String scope=compartment!=null && compartment.startsWith("ocid1.compartment.")?"in compartment id "+compartment:"in compartment id <COMPARTMENT_OCID>";
        String prefix="Allow group id <GROUP_OCID> to ";
        var result=new ArrayList<Rule>();
        java.util.function.BiConsumer<String,String> add=(task,permission)->result.add(new Rule(task,prefix+permission+" "+scope));
        if(menu.equals("topics")) {
            add.accept("알림 주제 조회","read ons-topics");
            result.add(new Rule("알림 주제 생성",prefix+"manage ons-topics "+scope+" where request.permission = 'ONS_TOPIC_CREATE'"));
        } else {
            add.accept("DevOps 목록·상세 조회","read devops-family");
            if(menu.equals("projects")) {
                add.accept("알림 주제 선택","read ons-topics");
                result.add(new Rule("프로젝트 생성",prefix+"manage devops-project "+scope+" where request.permission = 'DEVOPS_PROJECT_CREATE'"));
            }
            if(menu.equals("pipelines")) {
                add.accept("파이프라인·단계 생성 및 수정","manage devops-build-family");
                add.accept("산출물 참조 생성","manage devops-deploy-artifact");
                add.accept("파일 Registry 조회","read artifact-repositories");
                add.accept("파일 Registry 생성","manage artifact-repositories");
                add.accept("컨테이너 Registry 조회","read repos");
                add.accept("일반 산출물 조회","read generic-artifacts");
            }
            if(Set.of("execute","history","pipelines","guides").contains(menu)) {
                result.add(new Rule("빌드 실행",prefix+"manage devops-build-run "+scope+" where request.permission = 'DEVOPS_BUILD_RUN_CREATE'"));
                add.accept("로그 설정 조회","read log-groups");
                add.accept("로그 본문 조회","read log-content");
                add.accept("로그 활성화·생성 (콘솔 작업)","manage log-groups");
            }
        }
        return result;
    }
}
