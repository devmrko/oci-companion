package io.github.devmrko.companion.service;
import java.util.*;
public final class DeploymentVolumes {
 private DeploymentVolumes(){}
 public record FileVolume(String kind,String name,String path,boolean readOnly,Integer mode){}
 public record EmptyDir(String path,String sizeLimit){}
 public static int mode(String value){if(value==null||!value.matches("0[0-7]{3}"))throw Errors.DEPLOYMENT_INPUT.exception();return Integer.parseInt(value,8);}
 public static String size(String value){if(value==null||!value.matches("[1-9][0-9]{0,6}(Ki|Mi|Gi)"))throw Errors.DEPLOYMENT_INPUT.exception();return value;}
 public static List<FileVolume> parse(String value){
  if(value.length()>8192)throw Errors.DEPLOYMENT_INPUT.exception();var result=new ArrayList<FileVolume>();var paths=new HashSet<String>();
  for(String line:value.lines().filter(x->!x.isBlank()).toList()){
   String[] fields=line.strip().split("\\s+");
   if((fields.length!=4&&fields.length!=5)||!Set.of("secret","configmap").contains(fields[0])||!fields[1].matches("[a-z0-9]([a-z0-9.-]*[a-z0-9])?")||fields[1].length()>253||!Set.of("true","false").contains(fields[3])||!paths.add(fields[2])||result.size()>=32)throw Errors.DEPLOYMENT_INPUT.exception();
   path(fields[2]);result.add(new FileVolume(fields[0],fields[1],fields[2],Boolean.parseBoolean(fields[3]),fields.length==5?mode(fields[4]):null));
  }return List.copyOf(result);
 }
 public static List<EmptyDir> scratch(String value){if(value.length()>4096)throw Errors.DEPLOYMENT_INPUT.exception();var result=new ArrayList<EmptyDir>();var paths=new HashSet<String>();for(String line:value.lines().filter(x->!x.isBlank()).toList()){String[] fields=line.strip().split("\\s+");if(fields.length>2||!paths.add(fields[0])||result.size()>=32)throw Errors.DEPLOYMENT_INPUT.exception();path(fields[0]);result.add(new EmptyDir(fields[0],fields.length==2?size(fields[1]):""));}return List.copyOf(result);}
 public static List<String> emptyDirs(String value){return scratch(value).stream().map(EmptyDir::path).toList();}
 static void path(String path){if(!path.matches("/[A-Za-z0-9_./-]+")||path.contains("..")||path.equals("/")||path.length()>1024)throw Errors.DEPLOYMENT_INPUT.exception();}
 public static List<Map<String,String>> headers(String value){
  if(value.length()>8192)throw Errors.DEPLOYMENT_INPUT.exception();var result=new ArrayList<Map<String,String>>();var names=new HashSet<String>();
  for(String line:value.lines().filter(x->!x.isBlank()).toList()){
   int separator=line.indexOf('=');if(separator<1||result.size()>=16)throw Errors.DEPLOYMENT_INPUT.exception();String name=line.substring(0,separator).strip(),text=line.substring(separator+1).strip();
   if(!name.matches("[A-Za-z0-9-]+")||!names.add(name.toLowerCase(Locale.ROOT))||text.chars().anyMatch(Character::isISOControl))throw Errors.DEPLOYMENT_INPUT.exception();
   if(Set.of("authorization","cookie","proxy-authorization").contains(name.toLowerCase(Locale.ROOT)))throw Errors.DEPLOYMENT_INPUT.exception();
   result.add(Map.of("name",name,"value",text));
  }return List.copyOf(result);
 }
}
