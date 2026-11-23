package io.github.devmrko.companion.adapter;

import io.github.devmrko.companion.domain.Models.Connection;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.core.StreamReadFeature;

/** Local configuration only. Does not authenticate to an IdP or mutate cloud resources. */
@Component
public class EnvironmentProfiles {
    public record Field(String key,String label,boolean required,String kind,String hint,List<String> options) {}
    public record Template(String id,String name,List<Field> fields) {}
    public record Document(int schemaVersion,int templateVersion,String name,String template,Map<String,String> values) {
        public Document { values=values==null?Map.of():Collections.unmodifiableMap(new LinkedHashMap<>(values)); }
    }
    public record Stored(String id,String owner,long revision,Document document) {}
    public record Assessment(List<String> missing,List<String> errors) { public boolean complete(){return missing.isEmpty()&&errors.isEmpty();} }
    private final Path root;
    private final JsonMapper json=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
    private static final int MAX_BYTES=262144,MAX_PROFILES=100;
    private static final Set<java.nio.file.attribute.PosixFilePermission> FILE_MODE=PosixFilePermissions.fromString("rw-------"),DIR_MODE=PosixFilePermissions.fromString("rwx------");
    public EnvironmentProfiles(@Value("${companion.environment-profiles-dir:.data/environment-profiles}") String root){this.root=Path.of(root).toAbsolutePath().normalize();}
    private static Field field(String key,String label,boolean required,String kind,String hint,String... options){return new Field(key,label,required,kind,hint,List.of(options));}
    public List<Template> templates(){
        var base=List.of(field("environment","환경",true,"enum","개발·시험·운영을 명시합니다.","dev","test","prod"),
            field("runtime","실행 환경",true,"enum","프로파일은 실행 자원을 만들지 않습니다.","oke","compute"),
            field("region","실행 리전",true,"region","대상 리전 식별자"),
            field("compartment","컴파트먼트 OCID",true,"compartment","이 설정을 적용할 구획"),
            field("modelRegion","모델 처리 리전",true,"region","실행 리전과 다를 수 있습니다."),
            field("model","모델 ID",true,"identifier","사용할 모델 식별자"),
            field("namespace","Kubernetes namespace",false,"name","OKE 선택 시 필수"),
            field("telemetryEndpoint","관측 서비스 주소",false,"https","선택. 인증 정보 없는 HTTPS 주소"),
            field("telemetryCredentialRef","관측 인증 참조",false,"reference","주소를 지정하면 필수. credential:논리이름 또는 vault:Secret-OCID"));
        var demo=new ArrayList<>(base);demo.add(field("credentialRef","테스트 인증 참조",true,"reference","평문 토큰을 입력하지 않습니다."));
        var oidc=new ArrayList<>(base);oidc.addAll(List.of(field("issuer","로그인 발급자 주소",true,"https","IdP가 제공하는 issuer URL"),field("audience","허용 audience",true,"identifier","앱/API의 수신 대상 식별자"),field("clientId","로그인 client ID",true,"identifier","공개 식별자. client secret이 아닙니다."),field("allowedGroup","허용 그룹",true,"identifier","그룹/역할 매핑 적용은 후속 단계")));
        var service=new ArrayList<>(base);service.addAll(List.of(field("issuer","토큰 발급자 주소",true,"https","서비스 호출 토큰의 issuer"),field("audience","허용 audience",true,"identifier","수신 API 식별자"),field("requiredScope","필수 권한 scope",true,"identifier","허용할 작업의 scope"),field("credentialRef","서비스 인증 참조",true,"reference","참조만 저장; 토큰 발급/검증은 후속 단계")));
        return List.of(new Template("demo-token","개발·데모 토큰",List.copyOf(demo)),new Template("workforce-oidc","사내 사용자 OIDC",List.copyOf(oidc)),new Template("service-oauth","시스템 간 OAuth",List.copyOf(service)));
    }
    public Template template(String id){return templates().stream().filter(t->t.id().equals(id)).findFirst().orElseThrow(()->invalid("지원하는 템플릿을 선택하세요."));}
    public Assessment assess(Document d){
        if(d==null||d.schemaVersion()!=1||d.templateVersion()!=1)throw invalid("지원하지 않는 프로파일 버전입니다.");
        if(d.name()==null||d.name().isBlank()||d.name().length()>100||d.name().chars().anyMatch(Character::isISOControl))throw invalid("프로파일 이름을 확인하세요.");
        var fields=template(d.template()).fields();var keys=new HashSet<String>();fields.forEach(f->keys.add(f.key()));
        if(!keys.containsAll(d.values().keySet()))throw invalid("알 수 없는 설정 필드입니다. 비밀값은 저장할 수 없습니다.");
        var missing=new ArrayList<String>();var errors=new ArrayList<String>();
        for(var f:fields){String value=d.values().getOrDefault(f.key(),"");
            if(value==null||value.length()>1024||value.chars().anyMatch(Character::isISOControl)){errors.add(f.label()+": 값 형식을 확인하세요.");continue;}
            if(value.isBlank()){if(f.required()||(f.key().equals("namespace")&&"oke".equals(d.values().get("runtime")))||(f.key().equals("telemetryCredentialRef")&&!Objects.toString(d.values().get("telemetryEndpoint"),"").isBlank()))missing.add(f.label());continue;}
            boolean valid=switch(f.kind()){
                case "enum" -> f.options().contains(value);
                case "region" -> value.matches("[a-z][a-z0-9-]{1,63}");
                case "compartment" -> value.matches("ocid1\\.compartment\\.[A-Za-z0-9_.-]{1,240}");
                case "name" -> value.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");
                case "identifier" -> value.matches("[A-Za-z0-9][A-Za-z0-9._:/@ -]{0,254}");
                case "reference" -> value.matches("credential:[a-z0-9][a-z0-9._-]{0,79}|vault:ocid1\\.vaultsecret\\.[A-Za-z0-9_.-]{1,240}");
                case "https" -> https(value);
                default -> false;
            };
            if(!valid)errors.add(f.label()+": 값 형식을 확인하세요.");
        }
        if(d.template().equals("demo-token")&&"prod".equals(d.values().get("environment")))errors.add("개발·데모 인증은 운영 환경에 지정할 수 없습니다.");
        return new Assessment(List.copyOf(missing),List.copyOf(errors));
    }
    private static boolean https(String value){try{var u=URI.create(value);return "https".equals(u.getScheme())&&u.getHost()!=null&&u.getUserInfo()==null&&u.getQuery()==null&&u.getFragment()==null;}catch(Exception e){return false;}}
    private static IllegalArgumentException invalid(String text){return new IllegalArgumentException(text);}
    private static String owner(Connection c){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((c.demo()+"\n"+c.profile()+"\n"+c.tenancy()+"\n"+c.user()).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static void noLinks(Path p){for(var part=p.toAbsolutePath();part!=null;part=part.getParent())if(Files.isSymbolicLink(part))throw invalid("심볼릭 링크 저장 경로는 지원하지 않습니다.");}
    private static void privateDirectory(Path p)throws Exception{noLinks(p);if(!Files.exists(p))Files.createDirectories(p,PosixFilePermissions.asFileAttribute(DIR_MODE));if(!Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)||!Files.getPosixFilePermissions(p).equals(DIR_MODE))throw invalid("프로파일 폴더 권한을 0700으로 설정하세요.");}
    private Path directory(Connection c){return root.resolve(owner(c));}
    private Path path(Connection c,String id){if(id==null||!id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw invalid("프로파일 ID가 올바르지 않습니다.");return directory(c).resolve(id+".json");}
    public synchronized Stored get(Connection c,String id){try{
        var p=path(c,id);noLinks(p);privateDirectory(root);privateDirectory(directory(c));
        if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.size(p)>MAX_BYTES||!Files.getPosixFilePermissions(p).equals(FILE_MODE))throw invalid("프로파일 파일과 0600 권한을 확인하세요.");
        var s=json.readValue(Files.readString(p),Stored.class);if(!owner(c).equals(s.owner())||!id.equals(s.id())||s.revision()<1)throw invalid("프로파일 소유 범위를 확인하세요.");
        if(!assess(s.document()).errors().isEmpty())throw invalid("저장된 프로파일 형식을 확인하세요.");return s;
    }catch(IllegalArgumentException e){throw e;}catch(Exception e){throw invalid("프로파일을 읽지 못했습니다. 파일 형식·권한을 확인하세요.");}}
    public synchronized List<Stored> list(Connection c){try{
        noLinks(root);if(!Files.exists(root))return List.of();privateDirectory(root);
        var dir=directory(c);if(!Files.exists(dir))return List.of();privateDirectory(dir);
        try(var files=Files.list(dir)){var paths=files.filter(p->p.getFileName().toString().endsWith(".json")).limit(MAX_PROFILES+1).toList();if(paths.size()>MAX_PROFILES)throw invalid("프로파일은 최대 100개입니다.");var result=new ArrayList<Stored>();for(var p:paths)result.add(get(c,p.getFileName().toString().replaceFirst("\\.json$","")));result.sort(Comparator.comparing(s->s.document().name()));return List.copyOf(result);}
    }catch(IllegalArgumentException e){throw e;}catch(Exception e){throw invalid("프로파일 목록의 파일 형식·권한을 확인하세요.");}}
    public synchronized Stored save(Connection c,String id,long expected,Document d){
        var report=assess(d);if(!report.errors().isEmpty())throw invalid(String.join(" ",report.errors()));
        try{privateDirectory(root);var dir=directory(c);privateDirectory(dir);var lock=dir.resolve(".lock");noLinks(lock);
            try(var channel=FileChannel.open(lock,Set.of(StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS),PosixFilePermissions.asFileAttribute(FILE_MODE));var held=channel.lock()){
                boolean create=id==null||id.isBlank();if(create){if(expected!=0||list(c).size()>=MAX_PROFILES)throw invalid("생성 상태 또는 최대 개수를 확인하세요.");id=UUID.randomUUID().toString();}
                else{var old=get(c,id);if(old.revision()!=expected)throw invalid("다른 곳에서 수정되었습니다. 다시 열고 저장하세요.");if(!old.document().template().equals(d.template()))throw invalid("템플릿을 변경하려면 새 프로파일을 만드세요.");}
                var record=new Stored(id,owner(c),expected+1,d);String content=json.writerWithDefaultPrettyPrinter().writeValueAsString(record)+"\n";if(content.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw invalid("프로파일 크기 한도를 초과했습니다.");
                var target=path(c,id);noLinks(target);var temp=Files.createTempFile(dir,"profile-",".tmp",PosixFilePermissions.asFileAttribute(FILE_MODE));
                try{Files.writeString(temp,content);Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}return record;
            }
        }catch(IllegalArgumentException e){throw e;}catch(Exception e){throw invalid("프로파일을 저장하지 못했습니다. POSIX 파일 권한과 저장 경로를 확인하세요.");}
    }
    /** Parse an untrusted transfer document. Never saves, resolves secrets, or calls a remote endpoint. */
    public Document previewImport(byte[] content){
        if(content==null||content.length==0||content.length>MAX_BYTES)throw invalid("JSON 파일은 1~262144바이트여야 합니다.");
        try{
            var node=json.readTree(content);
            if(!node.isObject())throw invalid("JSON 설정 객체가 필요합니다.");
            var keys=new HashSet<String>();node.propertyNames().forEach(keys::add);
            if(!keys.equals(Set.of("schemaVersion","templateVersion","name","template","values")))throw invalid("내보낸 설정 필드만 가져올 수 있습니다. 소유 정보·비밀 필드는 제외하세요.");
            if(!node.get("schemaVersion").isIntegralNumber()||!node.get("templateVersion").isIntegralNumber()
                ||!node.get("name").isString()||!node.get("template").isString()||!node.get("values").isObject())throw invalid("설정 필드의 자료형을 확인하세요.");
            for(var value:node.get("values"))if(!value.isString())throw invalid("values에는 문자열만 입력하세요.");
            var d=json.treeToValue(node,Document.class);var report=assess(d);
            if(!report.errors().isEmpty())throw invalid(String.join(" ",report.errors()));
            return d;
        }catch(IllegalArgumentException e){throw e;}catch(Exception e){throw invalid("JSON 형식·중복 키·버전을 확인하세요. 파일 내용은 저장하지 않았습니다.");}
    }
    public String export(Connection c,String id){return json.writerWithDefaultPrettyPrinter().writeValueAsString(get(c,id).document());}
}
