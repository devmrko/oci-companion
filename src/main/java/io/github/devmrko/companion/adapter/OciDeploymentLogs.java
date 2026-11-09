package io.github.devmrko.companion.adapter;
import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.devops.requests.GetDeploymentRequest;
import com.oracle.bmc.logging.LoggingManagementClient;
import com.oracle.bmc.logging.model.OciService;
import com.oracle.bmc.logging.requests.*;
import com.oracle.bmc.loggingsearch.LogSearchClient;
import com.oracle.bmc.loggingsearch.model.SearchLogsDetails;
import com.oracle.bmc.loggingsearch.requests.SearchLogsRequest;
import com.oracle.bmc.retrier.RetryConfiguration;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.BuildLogs.*;
import io.github.devmrko.companion.port.DeploymentLogs;
import io.github.devmrko.companion.service.Errors;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class OciDeploymentLogs implements DeploymentLogs {
    private final OciBuildHistory clients;
    private static final ClientConfiguration CONFIG=ClientConfiguration.builder().connectionTimeoutMillis(5000).readTimeoutMillis(30000)
        .retryConfiguration(RetryConfiguration.NO_RETRY_CONFIGURATION).build();
    public OciDeploymentLogs(OciBuildHistory clients) { this.clients=clients; }
    public Page read(Connection c,String region,String compartment,String pipeline,String run,String page,String until) {
        // Only OCI-returned identifiers enter the query; request identifiers are checked against the run.
        try(var devops=clients.client(c,region)) {
            var r=devops.getDeployment(GetDeploymentRequest.builder().deploymentId(run).build()).getDeployment();
            if(!compartment.equals(r.getCompartmentId()) || !pipeline.equals(r.getDeployPipelineId())) throw Errors.SCOPE.exception();
            Instant end=until.isEmpty()?Instant.now():Instant.parse(until);
            if(end.isAfter(Instant.now().plusSeconds(5)) || end.isBefore(r.getTimeCreated().toInstant()) || page.length()>8192) throw Errors.INVALID.exception();
            try(var logging=LoggingManagementClient.builder().configuration(CONFIG).build(clients.authentication(c));
                var search=LogSearchClient.builder().configuration(CONFIG).build(clients.authentication(c))) {
                logging.setRegion(region);search.setRegion(region);
                var scopes=new ArrayList<String>();boolean disabled=false;
                for(var group:logging.getPaginators().listLogGroupsRecordIterator(ListLogGroupsRequest.builder().compartmentId(compartment).build()))
                    for(var log:logging.getPaginators().listLogsRecordIterator(ListLogsRequest.builder().logGroupId(group.getId()).build()))
                        if(log.getConfiguration()!=null && log.getConfiguration().getSource() instanceof OciService src
                            && "devops".equalsIgnoreCase(src.getService()) && r.getProjectId().equals(src.getResource())) {
                            if(Boolean.TRUE.equals(log.getIsEnabled())) scopes.add("\""+compartment+"/"+group.getId()+"/"+log.getId()+"\"");
                            else disabled=true;
                        }
                if(scopes.isEmpty()) return new Page("NOT_CONFIGURED",disabled?"프로젝트 로그가 비활성화되어 있습니다.":"현재 구획에서 접근 가능한 프로젝트 로그를 찾지 못했습니다. 로그 위치·권한·활성화를 확인하세요.",List.of(),"",end.toString());
                String query="search "+String.join(", ",scopes)+" | where data.deploymentId = '"+r.getId()+"' | sort by datetime desc";
                var response=search.searchLogs(SearchLogsRequest.builder().searchLogsDetails(SearchLogsDetails.builder()
                    .timeStart(Date.from(r.getTimeCreated().toInstant().minusSeconds(60))).timeEnd(Date.from(end))
                    .searchQuery(query).isReturnFieldInfo(false).build()).limit(1000).page(page.isEmpty()?null:page).build());
                var names=new HashMap<String,String>();
                if(r.getDeploymentExecutionProgress()!=null&&r.getDeploymentExecutionProgress().getDeployStageExecutionProgress()!=null)
                    r.getDeploymentExecutionProgress().getDeployStageExecutionProgress().forEach((id,progress)->names.put(id,Objects.toString(progress.getDeployStageDisplayName(),id)));
                var lines=new ArrayList<Line>();
                for(var result:response.getSearchResponse().getResults()) {
                    var row=map(result.getData());var content=map(row.get("logContent"));var data=map(content.get("data"));
                    String stage=text(data.containsKey("deployStageId")?data.get("deployStageId"):data.get("stageName"));
                    lines.add(new Line(text(content.containsKey("time")?content.get("time"):row.get("datetime")),names.getOrDefault(stage,stage.isEmpty()?"공통":stage),text(data.get("message"))));
                }
                lines.sort(Comparator.comparing(Line::time));
                return new Page(lines.isEmpty()?"EMPTY":"READY",lines.isEmpty()?"로그가 아직 없거나 반영이 지연 중입니다. 보존 기간도 확인하세요.":"한 번에 최대 1,000건 · 시간순",
                    lines,Objects.toString(response.getOpcNextPage(),""),end.toString());
            }
        } catch(com.oracle.bmc.model.BmcException e) {
            String state=e.getStatusCode()==401?"AUTH":Set.of(403,404).contains(e.getStatusCode())?"ACCESS_OR_MISSING":"ERROR";
            return new Page(state,"로그 조회 실패 · HTTP "+e.getStatusCode()+" · 권한·리전·리소스 확인. 요청 ID: "+Objects.toString(e.getOpcRequestId(),""),List.of(),"","");
        } catch(io.github.devmrko.foundation.core.error.BusinessException e) { throw e; }
        catch(Exception e) { return new Page("ERROR","로그 조회 실패 · 연결과 Logging 설정을 확인하세요.",List.of(),"",""); }
    }
    private static Map<?,?> map(Object o) { return o instanceof Map<?,?> m?m:Map.of(); }
    private static String text(Object o) { return Objects.toString(o,""); }
}
