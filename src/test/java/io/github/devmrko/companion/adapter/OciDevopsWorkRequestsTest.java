package io.github.devmrko.companion.adapter;
import com.oracle.bmc.devops.DevopsClient;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.responses.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OciDevopsWorkRequestsTest {
 GetWorkRequestResponse state(OperationStatus status){return GetWorkRequestResponse.builder().workRequest(WorkRequest.builder().status(status).build()).build();}
 @Test void acceptedAndInProgressMustFinishBeforeCallerCanDeploy(){var d=mock(DevopsClient.class);var elapsed=new AtomicLong();when(d.getWorkRequest(any())).thenReturn(state(OperationStatus.Accepted),state(OperationStatus.InProgress),state(OperationStatus.Succeeded));OciDevopsWorkRequests.await(d,"work",elapsed::get,elapsed::addAndGet,5000);assertEquals(1000,elapsed.get());verify(d,times(3)).getWorkRequest(any());}
 @Test void neverCompletedAndMissingWorkRequestFailClosed(){var d=mock(DevopsClient.class);assertThrows(IllegalStateException.class,()->OciDevopsWorkRequests.await(d,null));verifyNoInteractions(d);var elapsed=new AtomicLong();when(d.getWorkRequest(any())).thenReturn(state(OperationStatus.InProgress));assertThrows(IllegalStateException.class,()->OciDevopsWorkRequests.await(d,"work",elapsed::get,elapsed::addAndGet,1000));verify(d,never()).createDeployment(any());}
 @Test void failedWorkPreservesRedactedOciError(){var d=mock(DevopsClient.class);when(d.getWorkRequest(any())).thenReturn(state(OperationStatus.Failed));when(d.listWorkRequestErrors(any())).thenReturn(ListWorkRequestErrorsResponse.builder().workRequestErrorCollection(WorkRequestErrorCollection.builder().items(List.of(WorkRequestError.builder().code("InvalidManifest").message("invalid reference ocid1.test.oc1.secret").build())).build()).build());var error=assertThrows(IllegalStateException.class,()->OciDevopsWorkRequests.await(d,"work"));assertTrue(error.getMessage().contains("InvalidManifest"));assertFalse(error.getMessage().contains("ocid1.test.oc1.secret"));}
}
