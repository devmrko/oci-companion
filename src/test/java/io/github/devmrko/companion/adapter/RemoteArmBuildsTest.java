package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.port.ArmBuilds.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.commons.compress.archivers.tar.*;
import static org.junit.jupiter.api.Assertions.*;
class RemoteArmBuildsTest {
 @TempDir Path directory;
 byte[] zip(Map<String,String> files)throws Exception{var out=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(out)){for(var e:files.entrySet()){zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));zip.closeEntry();}}return out.toByteArray();}
 @Test void sourceArchiveRejectsTraversalAndOmitsEnvironmentFiles()throws Exception{
  var tar=directory.resolve("source.tar");assertThrows(Exception.class,()->ArmBuildSource.tar(new ByteArrayInputStream(zip(Map.of("../escape","bad"))),tar));
  ArmBuildSource.tar(new ByteArrayInputStream(zip(Map.of("repo/Dockerfile","FROM scratch","repo/.env","secret"))),tar);
  try(var in=new TarArchiveInputStream(Files.newInputStream(tar))){assertEquals("repo/Dockerfile",in.getNextEntry().getName());assertNull(in.getNextEntry());}
 }
 void executable(String name,String text)throws Exception{Path file=directory.resolve(name);Files.writeString(file,text);Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rwx------"));}
 String execute(String test,boolean success)throws Exception {
  executable("uname","#!/bin/sh\necho aarch64\n");
  executable("podman","#!/bin/sh\ncase \"$1\" in\nlogin) cat >/dev/null;;\nbuild) test -f tested || exit 9;;\npush) if [ -f fail-push ]; then echo 'Error: registry unreachable' >&2; exit 125; fi; shift; while [ \"$#\" -gt 0 ]; do if [ \"$1\" = --digestfile ]; then shift; printf '%s\\n' 'sha256:"+"a".repeat(64)+"' > \"$1\"; break; fi; shift; done;;\nesac\n");
  Path work=Files.createDirectory(directory.resolve("work"));ArmBuildSource.tar(new ByteArrayInputStream(zip(Map.of("repo/Dockerfile","FROM scratch"))),work.resolve("source.tar"));
  var input=new Input("arm","source","a".repeat(40),"repo","verify","Dockerfile",test);var b=new Builder("arm","host","user","/key");var plan=new Plan(input,"example.ocir.io/ns/app:verify","source","arm",b);
  var process=new ProcessBuilder("/bin/bash","-s").redirectErrorStream(true);process.environment().put("PATH",directory+":"+System.getenv("PATH"));var running=process.start();try(var out=running.getOutputStream()){out.write(RemoteArmBuilds.script(plan,work.toString(),"user","private-token".toCharArray()).getBytes(StandardCharsets.UTF_8));}
  assertTrue(running.waitFor(10,TimeUnit.SECONDS));String output=new String(running.getInputStream().readAllBytes(),StandardCharsets.UTF_8);assertEquals(success,running.exitValue()==0,output);assertFalse(output.contains("private-token"));assertFalse(Files.exists(work));return output;
 }
 @Test void testsPrecedeBuildPushAndDigestAndTemporaryFilesAreRemoved()throws Exception{String output=execute("printf passed > tested",true);assertTrue(output.indexOf("COMPANION_PHASE=test")<output.indexOf("COMPANION_PHASE=build"));assertTrue(output.indexOf("COMPANION_PHASE=build")<output.indexOf("COMPANION_PHASE=push"));assertTrue(output.contains("COMPANION_DIGEST=sha256:"+"a".repeat(64)));}
 @Test void failedTestsPreventBuildAndPush()throws Exception{String output=execute("exit 7",false);assertFalse(output.contains("COMPANION_PHASE=build"));assertFalse(output.contains("COMPANION_DIGEST="));}
 @Test void registryAuthenticationFailureDoesNotExposeRawLogOrToken()throws Exception{String output=execute("echo 'unauthorized private-token'; exit 125",false);assertTrue(output.contains("COMPANION_ERROR=registry-auth"));assertFalse(output.contains("unauthorized"));}
 @Test void pushFailureReturnsOnlyBoundedDedicatedPushError()throws Exception{String output=execute("touch tested fail-push",false);String encoded=output.lines().filter(x->x.startsWith("COMPANION_PUSH_ERROR=")).findFirst().orElseThrow().substring(21);assertTrue(new String(Base64.getDecoder().decode(encoded),StandardCharsets.UTF_8).contains("registry unreachable"));assertFalse(output.contains("private-token"));}
 @Test void invalidCommitPathsAndBuilderOptionsAreRejected(){assertThrows(RuntimeException.class,()->RemoteArmBuilds.validate(new Input("arm","source","main","repo","tag","Dockerfile","true")));assertThrows(RuntimeException.class,()->RemoteArmBuilds.validate(new Input("arm","source","a".repeat(40),"repo","tag","../Dockerfile","true")));assertThrows(RuntimeException.class,()->RemoteArmBuilds.validate(new Builder("arm","-oProxyCommand=x","opc","/key")));}
}
