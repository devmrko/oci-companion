package io.github.devmrko.companion.adapter;
import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.retrier.RetryConfiguration;
import com.oracle.bmc.core.BlockstorageClient;
import com.oracle.bmc.core.model.*;
import com.oracle.bmc.core.requests.*;
import com.oracle.bmc.identity.IdentityClient;
import com.oracle.bmc.identity.requests.ListAvailabilityDomainsRequest;
import io.github.devmrko.companion.domain.Models.Connection;
import io.github.devmrko.companion.port.VolumeBackups;
import io.github.devmrko.companion.service.Errors;
import io.github.devmrko.companion.error.BusinessException;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class OciVolumeBackups implements VolumeBackups {
 private final OciBuildHistory clients;
 public OciVolumeBackups(OciBuildHistory clients){this.clients=clients;}
 private ClientConfiguration config(){return ClientConfiguration.builder().connectionTimeoutMillis(5000).readTimeoutMillis(10000).retryConfiguration(RetryConfiguration.NO_RETRY_CONFIGURATION).build();}
 BlockstorageClient storage(Connection c,String region)throws Exception{var x=BlockstorageClient.builder().configuration(config()).build(clients.authentication(c));x.setRegion(region);return x;}
 List<String> domains(Connection c,String region,String comp)throws Exception{try(var x=IdentityClient.builder().configuration(config()).build(clients.authentication(c))){x.setRegion(region);return x.listAvailabilityDomains(ListAvailabilityDomainsRequest.builder().compartmentId(comp).build()).getItems().stream().map(a->a.getName()).toList();}}
 public Catalog list(Connection c,String region,String comp){try(var x=storage(c,region)){
  var volumes=new ArrayList<Volume>();for(var v:x.getPaginators().listVolumesRecordIterator(ListVolumesRequest.builder().compartmentId(comp).build()))if(!"TERMINATED".equals(v.getLifecycleState().getValue()))volumes.add(new Volume(v.getId(),v.getDisplayName(),v.getLifecycleState().getValue(),v.getSizeInGBs(),v.getAvailabilityDomain()));
  var backups=new ArrayList<Backup>();for(var b:x.getPaginators().listVolumeBackupsRecordIterator(ListVolumeBackupsRequest.builder().compartmentId(comp).build()))if(!"TERMINATED".equals(b.getLifecycleState().getValue()))backups.add(new Backup(b.getId(),b.getDisplayName(),b.getLifecycleState().getValue(),b.getSizeInGBs(),b.getVolumeId(),String.valueOf(b.getType()),b.getTimeCreated()==null?"":b.getTimeCreated().toInstant().toString()));
  volumes.sort(Comparator.comparing(Volume::name,Comparator.nullsLast(String::compareTo)));backups.sort(Comparator.comparing(Backup::created).reversed());return new Catalog(List.copyOf(volumes),List.copyOf(backups),domains(c,region,comp));
 }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}}
 public Plan plan(Connection c,String region,String comp,Input i){
  if(i==null||i.operation()==null||i.target()==null||i.target().isBlank()||i.name()==null||i.name().isBlank()||i.name().length()>255||i.name().chars().anyMatch(Character::isISOControl))throw Errors.BACKUP_INPUT.exception();
  try(var x=storage(c,region)){
   if("BACKUP".equals(i.operation())){
    if(i.type()==null||!Set.of("FULL","INCREMENTAL").contains(i.type())||i.domain()==null||!i.domain().isEmpty())throw Errors.BACKUP_INPUT.exception();
    var r=x.getVolume(GetVolumeRequest.builder().volumeId(i.target()).build());var v=r.getVolume();if(!comp.equals(v.getCompartmentId()))throw Errors.SCOPE.exception();if(!"AVAILABLE".equals(v.getLifecycleState().getValue()))throw Errors.BUILD_CHANGED.exception();return new Plan(i,v.getDisplayName(),v.getSizeInGBs(),r.getEtag());
   }
   if("RESTORE".equals(i.operation())){
    if(!"".equals(i.type())||i.domain()==null||!domains(c,region,comp).contains(i.domain()))throw Errors.BACKUP_INPUT.exception();
    var r=x.getVolumeBackup(GetVolumeBackupRequest.builder().volumeBackupId(i.target()).build());var b=r.getVolumeBackup();if(!comp.equals(b.getCompartmentId()))throw Errors.SCOPE.exception();if(!"AVAILABLE".equals(b.getLifecycleState().getValue()))throw Errors.BUILD_CHANGED.exception();return new Plan(i,b.getDisplayName(),b.getSizeInGBs(),r.getEtag());
   }
   throw Errors.BACKUP_INPUT.exception();
  }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.CLOUD.exception();}
 }
 public String apply(Connection c,String region,String comp,Plan p,String token){
  if(p.etag()==null||p.etag().isBlank()||!p.equals(plan(c,region,comp,p.input())))throw Errors.BUILD_CHANGED.exception();
  try(var x=storage(c,region)){var i=p.input();
   if("BACKUP".equals(i.operation()))return x.createVolumeBackup(CreateVolumeBackupRequest.builder().opcRetryToken(token).createVolumeBackupDetails(CreateVolumeBackupDetails.builder().volumeId(i.target()).displayName(i.name()).type(CreateVolumeBackupDetails.Type.create(i.type())).build()).build()).getVolumeBackup().getId();
   return x.createVolume(CreateVolumeRequest.builder().opcRetryToken(token).createVolumeDetails(CreateVolumeDetails.builder().compartmentId(comp).availabilityDomain(i.domain()).displayName(i.name()).sourceDetails(VolumeSourceFromVolumeBackupDetails.builder().id(i.target()).build()).sizeInGBs(p.size()).build()).build()).getVolume().getId();
  }catch(BusinessException e){throw e;}catch(Exception e){throw Errors.BACKUP_UNKNOWN.exception();}
 }
}
