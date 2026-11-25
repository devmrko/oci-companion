package io.github.devmrko.companion.service;

import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.*;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Uses the existing owner-scoped, atomic local draft storage implementation. */
@Service
public class OkeDeploymentInputs {
 private final BuildSpecDraftStore store;
 private final tools.jackson.databind.json.JsonMapper json=tools.jackson.databind.json.JsonMapper.builder().build();
 public OkeDeploymentInputs(BuildSpecDraftStore store){this.store=store;}
 private String key(Connection c,String region,String compartment,String environment){return String.join("\n","oke-deployment-input-v1",Boolean.toString(c.demo()),c.profile(),c.tenancy(),c.user(),region,compartment,environment);}
 public Optional<OkeDeploymentSetup.Input> read(Connection c,String region,String compartment,String environment){
  if(environment.isBlank())return Optional.empty();
  return store.read(key(c,region,compartment,environment)).map(d->{var i=json.readValue(d.content(),OkeDeploymentSetup.Input.class);if(!environment.equals(i.environment()))throw Errors.SCOPE.exception();return i;});
 }
 public void save(Connection c,String region,String compartment,OkeDeploymentSetup.Input input){
  store.save(key(c,region,compartment,input.environment()),new BuildSpecDraftStore.Draft("oke-input-v1",json.writeValueAsString(input),Instant.now().toString()));
 }
}
