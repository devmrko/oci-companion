package io.github.devmrko.companion.port;
import io.github.devmrko.companion.domain.Models.Connection;
import java.util.List;
public interface ArmBuilds {
 record Builder(String name,String host,String user,String keyPath,String secretEnvironment,String secretNamespace,String secretName,String secretEndpoint,String kind,String functionId){
  public Builder(String name,String host,String user,String keyPath,String secretEnvironment,String secretNamespace,String secretName,String secretEndpoint){this(name,host,user,keyPath,secretEnvironment,secretNamespace,secretName,secretEndpoint,"VM","");}
  public Builder(String name,String host,String user,String keyPath){this(name,host,user,keyPath,"","","","PRIVATE_ENDPOINT");}
  public Builder {kind=kind==null||kind.isBlank()?"VM":kind;functionId=functionId==null?"":functionId;secretEnvironment=secretEnvironment==null?"":secretEnvironment;secretNamespace=secretNamespace==null?"":secretNamespace;secretName=secretName==null?"":secretName;secretEndpoint=secretEndpoint==null||secretEndpoint.isBlank()?"PRIVATE_ENDPOINT":secretEndpoint;}
 }
 record Input(String builder,String sourceRepository,String commit,String imageRepository,String tag,String dockerfile,String testCommand){}
 record Plan(Input input,String image,String sourceName,String builderName,Builder target,String functionFingerprint){public Plan(Input input,String image,String sourceName,String builderName,Builder target){this(input,image,sourceName,builderName,target,"");}}
 record Run(String id,String state,String phase,String image,String repository,String digest,String error,String startedAt,String finishedAt){}
 List<Builder> builders(Connection c,String region,String compartment);
 void saveBuilder(Connection c,String region,String compartment,Builder builder);
 default void removeBuilder(Connection c,String region,String compartment,String name){throw new UnsupportedOperationException();}
 Plan preview(Connection c,String region,String compartment,Input input);
 String start(Connection c,String region,String compartment,Plan plan,String username,char[] password);
 Run read(Connection c,String region,String compartment,String id);
 List<String> managedImages();
}
