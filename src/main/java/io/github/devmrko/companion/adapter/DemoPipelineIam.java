package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.PipelineIam;
import io.github.devmrko.companion.service.Errors;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
@Component @Profile("demo")
public class DemoPipelineIam implements PipelineIam {
    public Check inspect(Connection c,String region,String compartment,String pipeline){return new Check(true,false,"demo",false,"",List.of(),List.of("데모: 실제 IAM을 조회하지 않았습니다."),"");}
    public void create(Connection c,String region,String compartment,String pipeline,Check preview,String token){throw Errors.INVALID.exception();}
}
