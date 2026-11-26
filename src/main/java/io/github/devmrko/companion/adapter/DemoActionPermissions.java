package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.ActionPermissions;
import io.github.devmrko.companion.service.Readiness;
@org.springframework.stereotype.Component @org.springframework.context.annotation.Profile("demo")
public class DemoActionPermissions implements ActionPermissions {
 public Evidence inspect(Connection c,String compartment,String resource){return new Evidence(Readiness.Permission.UNKNOWN,"데모: 실제 관리 권한 미확인","관리자에게 "+resource+" 관리 권한을 요청하세요.");}
}
