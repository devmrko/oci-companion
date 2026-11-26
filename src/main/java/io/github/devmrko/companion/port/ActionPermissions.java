package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.service.Readiness;
public interface ActionPermissions {
 record Evidence(Readiness.Permission permission,String reason,String guidance) {}
 default Evidence securityList(Connection actor,String region,String list){return new Evidence(Readiness.Permission.UNKNOWN,"보안 목록 관리 권한 미확인","Allow group <관리자 그룹> to manage security-lists in compartment id <보안 목록 구획 OCID>");}
 Evidence inspect(Connection actor,String compartment,String resource);
}
