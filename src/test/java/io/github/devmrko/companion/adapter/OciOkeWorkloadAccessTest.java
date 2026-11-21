package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.port.OkeWorkloadAccess.Input;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import tools.jackson.databind.json.JsonMapper;
class OciOkeWorkloadAccessTest {
 final OciOkeWorkloadAccess adapter=new OciOkeWorkloadAccess(null,null,null,null,null);
 final Input input=new Input("env","PUBLIC_ENDPOINT","app","reader","test-bucket");
 @Test void ownerFitsKubernetesLabelAndIsScopeBound(){var c=new io.github.devmrko.companion.domain.Models.Connection("DEFAULT","tenancy","user","r","name",false);assertThat(OciOkeWorkloadAccess.owner(c,"r","c")).hasSize(48).matches("[a-f0-9]+").isNotEqualTo(OciOkeWorkloadAccess.owner(c,"r","other"));}
 @Test void policyRestrictsIdentityAndBucket(){String p=OciOkeWorkloadAccess.policy("ocid1.compartment.oc1.test","ocid1.cluster.oc1.ap-seoul-1.test",input);assertThat(p).contains("inspect objects","principal.type='workload'","principal.cluster_id='ocid1.cluster.oc1.ap-seoul-1.test'","principal.namespace='app'","principal.service_account='reader'","target.bucket.name='test-bucket'").doesNotContain("manage","read secret");assertThatThrownBy(()->OciOkeWorkloadAccess.policy("ocid1.compartment.oc1.test","ocid1.cluster.oc1.ap-seoul-1.test",new Input("env","PUBLIC_ENDPOINT","app","reader","x'"))).isInstanceOf(RuntimeException.class);}
 @Test void manifestUsesNativeIdentityAndBoundedJob()throws Exception{var j=JsonMapper.builder().build().readTree(adapter.manifest(input,"namespace","ap-seoul-1","job","owner"));assertThat(j.at("/spec/activeDeadlineSeconds").asInt()).isEqualTo(120);assertThat(j.at("/spec/ttlSecondsAfterFinished").asInt()).isEqualTo(3600);assertThat(j.at("/spec/template/spec/serviceAccountName").asText()).isEqualTo("reader");assertThat(j.at("/spec/template/spec/containers/0/image").asText()).contains("@sha256:");assertThat(j.toString()).contains("get_oke_workload_identity_resource_principal_signer").doesNotContain("private_key","api_key","hostPath");}
 @Test void resultNeverEchoesRawLogOrUnverifiedSuccess(){assertThat(adapter.decode("secret-token").state()).isEqualTo("UNKNOWN");assertThat(adapter.decode("secret-token").note()).doesNotContain("secret-token");assertThat(adapter.decode("{\"schema\":1,\"operation\":\"ListObjects\",\"state\":\"GRANTED\",\"httpStatus\":403}").state()).isEqualTo("UNKNOWN");assertThat(adapter.decode("{\"schema\":1,\"operation\":\"ListObjects\",\"state\":\"GRANTED\",\"httpStatus\":200}").state()).isEqualTo("GRANTED");}
}
