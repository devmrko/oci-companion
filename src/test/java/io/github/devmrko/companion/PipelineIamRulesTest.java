package io.github.devmrko.companion;

import io.github.devmrko.companion.service.PipelineIamRules;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PipelineIamRulesTest {
    private final String pipeline="ocid1.devopsbuildpipeline.oc1.test";
    private final List<PipelineIamRules.Group> groups=List.of(new PipelineIamRules.Group("group-id","builders",PipelineIamRules.rule("comp")));
    private List<String> missing(String... statements){return PipelineIamRules.missing(groups,List.of(statements),Set.of("comp","parent"),pipeline,"comp","builders","in compartment id comp");}
    @Test void noPoliciesReportsAllSixRequirements(){assertEquals(6,missing().size());}
    @Test void higherPrivilegeAndAncestorScopeCoverRequirements(){assertTrue(missing("Allow dynamic-group builders to manage all-resources in compartment id parent").isEmpty());assertTrue(missing("Allow dynamic-group id group-id to manage all-resources in tenancy").isEmpty());}
    @Test void rejectsOtherPrincipalsScopesAndConditionalGrants(){
        assertEquals(6,missing("Allow group builders to manage all-resources in tenancy", "Allow dynamic-group other to manage all-resources in tenancy", "Allow dynamic-group builders to manage all-resources in compartment id unrelated", "Allow dynamic-group builders to manage all-resources in tenancy where request.permission = 'X'").size());
    }
    @Test void reportsOnlyMissingLinesAndHonorsVerbHierarchy(){var lines=missing("Allow dynamic-group builders to manage all-artifacts in compartment id comp", "Allow dynamic-group builders to read repos in tenancy");assertEquals(5,lines.size());assertTrue(lines.stream().noneMatch(s->s.contains("all-artifacts")));assertTrue(lines.stream().anyMatch(s->s.contains("manage repos")));}
    @Test void rulesBindResourceTypeAndCompartment(){assertTrue(PipelineIamRules.matches(PipelineIamRules.rule("comp"),pipeline,"comp"));assertFalse(PipelineIamRules.matches(PipelineIamRules.rule("other"),pipeline,"comp"));assertFalse(PipelineIamRules.matches("ALL {resource.id = 'different', resource.type = 'devopsbuildpipeline'}",pipeline,"comp"));assertTrue(PipelineIamRules.matches("resource.id = '"+pipeline+"'",pipeline,"comp"));assertFalse(PipelineIamRules.matches("tag.team.value = 'build'",pipeline,"comp"));}
    @Test void failedRunDoesNotLeaveAcceptedStageButPreservesCompletedStages(){assertEquals("FAILED",PipelineIamRules.stageStatus("FAILED","ACCEPTED"));assertEquals("SUCCEEDED",PipelineIamRules.stageStatus("FAILED","SUCCEEDED"));assertEquals("ACCEPTED",PipelineIamRules.stageStatus("ACCEPTED","ACCEPTED"));}
    @Test void errorTextRedactsIdentifiersAndSecrets(){assertEquals("RelatedResourceNotAuthorizedOrNotFound [OCID] token=[REDACTED]",PipelineIamRules.safe("RelatedResourceNotAuthorizedOrNotFound ocid1.pipeline.oc1.test token=hidden"));}
    @Test void cloudSnapshotsOmitEncodedManifestsAndJsonCredentials(){
        String result=PipelineIamRules.safe("{\"password\": \"private-value\",\"base64EncodedContent\": \"encoded-private-value\",\"id\":\"ocid1.pipeline.oc1.test\"}");
        assertFalse(result.contains("private-value"));assertFalse(result.contains("ocid1."));assertTrue(result.contains("[OMITTED]"));assertTrue(result.contains("[REDACTED]"));
    }
}
