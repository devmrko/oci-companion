package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.service.Errors;
import com.oracle.bmc.devops.model.*;
import com.oracle.bmc.devops.requests.*;
final class OkeEnvironmentTarget {
 static OkeWorkloadHealth.Target prepare(OciBuildHistory clients,OciOkeWorkloadConnections connections,Connection c,String region,String compartment,String environment,String endpoint,String namespace)throws Exception {
  try(var d=clients.client(c,region)){
   var e=d.getDeployEnvironment(GetDeployEnvironmentRequest.builder().deployEnvironmentId(environment).build()).getDeployEnvironment();
   if(!(e instanceof OkeClusterDeployEnvironment oke)||!compartment.equals(e.getCompartmentId())||e.getLifecycleState()!=DeployEnvironment.LifecycleState.Active)throw Errors.SCOPE.exception();
   return connections.prepare(c,region,compartment,oke.getClusterId(),endpoint,namespace,"unused");
  }
 }
}
