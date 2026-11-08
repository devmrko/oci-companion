package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.domain.Models.Connection;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class AgentConnectionRegistry {
 public record Registration(String id,String name,String environment,String adapter,String baseUrl,String credentialRef,String profile,String tenancy,String user,String region,String compartment,List<Dependency> dependencies) {
  public Registration { dependencies=dependencies==null?List.of():List.copyOf(dependencies); }
  public Registration(String id,String name,String environment,String adapter,String baseUrl,String credentialRef,String profile,String tenancy,String user,String region,String compartment){this(id,name,environment,adapter,baseUrl,credentialRef,profile,tenancy,user,region,compartment,List.of());}
 }
 public record Dependency(String type,String label,String ocid) {
  public Dependency {
   String prefix=switch(Objects.toString(type,"")){case "VM"->"ocid1.instance.";case "ADB"->"ocid1.autonomousdatabase.";case "OKE"->"ocid1.cluster.";default->throw new IllegalArgumentException("VM·ADB·OKE 중 선택하세요.");};
   if(label==null||label.isBlank()||label.length()>100||ocid==null||ocid.length()>255||!ocid.startsWith(prefix)||!ocid.matches("[A-Za-z0-9._-]+"))throw new IllegalArgumentException("이름과 유형에 맞는 OCID를 확인하세요.");
  }
 }
 public record Credential(String id,String label,String tokenFile,String profile,String tenancy,String user,String region,String compartment) {}
 private final Path file,credentials;
 private final JsonMapper mapper=JsonMapper.builder().build();
 public AgentConnectionRegistry(@Value("${companion.agent-connections:.tools/agent-connections.json}") String file,@Value("${companion.agent-credentials:.tools/agent-credentials.json}") String credentials){this.file=Path.of(file);this.credentials=Path.of(credentials);}
 private boolean scope(Connection c,String r,String comp,String profile,String tenancy,String user,String region,String compartment){return c.profile().equals(profile)&&c.tenancy().equals(tenancy)&&c.user().equals(user)&&r.equals(region)&&comp.equals(compartment);}
 private <T> T[] read(Path path,Class<T[]> type)throws Exception{if(!Files.exists(path))return mapper.readValue("[]",type);if(Files.isSymbolicLink(path)||Files.size(path)>1_000_000)throw new IllegalArgumentException("Invalid registry");return mapper.readValue(Files.readString(path),type);}
 public synchronized List<Registration> list(Connection c,String r,String comp){try{return Arrays.stream(read(file,Registration[].class)).filter(x->scope(c,r,comp,x.profile(),x.tenancy(),x.user(),x.region(),x.compartment())).toList();}catch(Exception e){throw new IllegalArgumentException("연결 목록을 읽지 못했습니다.");}}
 public List<Credential> credentials(Connection c,String r,String comp){try{return Arrays.stream(read(credentials,Credential[].class)).filter(x->scope(c,r,comp,x.profile(),x.tenancy(),x.user(),x.region(),x.compartment())).toList();}catch(Exception e){throw new IllegalArgumentException("인증 참조 설정을 확인하세요.");}}
 public Registration get(Connection c,String r,String comp,String id){return list(c,r,comp).stream().filter(x->x.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("현재 범위에 등록된 연결이 없습니다."));}
 public String token(Connection c,String r,String comp,Registration x)throws Exception{var ref=credentials(c,r,comp).stream().filter(t->t.id().equals(x.credentialRef())).findFirst().orElseThrow();var path=Path.of(ref.tokenFile());if(Files.size(path)>8192||Files.isSymbolicLink(path))throw new IllegalArgumentException();String token=Files.readString(path).strip();if(token.isBlank()||token.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException();return token;}
 private void write(List<Registration> items)throws Exception{Files.createDirectories(file.toAbsolutePath().getParent());if(Files.isSymbolicLink(file))throw new IllegalArgumentException();var tmp=Files.createTempFile(file.toAbsolutePath().getParent(),"agent-connections-",".tmp",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));try{Files.writeString(tmp,mapper.writeValueAsString(items));Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(tmp);}}
 public synchronized String register(Connection c,String r,String comp,String name,String environment,String address,String credential){
  if(name==null||name.isBlank()||name.length()>100||environment==null||environment.isBlank()||environment.length()>80)throw new IllegalArgumentException("이름과 환경을 확인하세요.");
  MonitoringTransport.checkedBase(address);
  if(credentials(c,r,comp).stream().noneMatch(x->x.id().equals(credential)))throw new IllegalArgumentException("현재 범위의 인증 참조를 선택하세요.");
  try{var items=new ArrayList<>(Arrays.asList(read(file,Registration[].class)));if(items.size()>=100)throw new IllegalArgumentException();String id=UUID.randomUUID().toString();items.add(new Registration(id,name,environment,"monitoring-http-v1",address,credential,c.profile(),c.tenancy(),c.user(),r,comp));write(items);return id;}catch(Exception e){throw new IllegalArgumentException("연결 등록을 저장하지 못했습니다.");}
 }
 public synchronized void remove(Connection c,String r,String comp,String id){get(c,r,comp,id);try{var items=new ArrayList<>(Arrays.asList(read(file,Registration[].class)));items.removeIf(x->x.id().equals(id));write(items);}catch(Exception e){throw new IllegalArgumentException("연결 해제를 저장하지 못했습니다.");}}
 public synchronized void dependency(Connection c,String r,String comp,String id,Dependency dependency,boolean remove){
  var current=get(c,r,comp,id);var refs=new ArrayList<>(current.dependencies());
  if(remove)refs.removeIf(x->x.ocid().equals(dependency.ocid()));
  else {if(refs.size()>=8||refs.stream().anyMatch(x->x.ocid().equals(dependency.ocid())))throw new IllegalArgumentException("의존 리소스는 중복 없이 최대 8개입니다.");refs.add(dependency);}
  try{var all=new ArrayList<>(Arrays.asList(read(file,Registration[].class)));all.replaceAll(x->x.id().equals(id)?new Registration(x.id(),x.name(),x.environment(),x.adapter(),x.baseUrl(),x.credentialRef(),x.profile(),x.tenancy(),x.user(),x.region(),x.compartment(),refs):x);write(all);}catch(Exception e){throw new IllegalArgumentException("의존 리소스를 저장하지 못했습니다.");}
 }

}
