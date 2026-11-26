package io.github.devmrko.companion.adapter;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import org.apache.commons.compress.archivers.tar.*;
/** Bounded source transfer: reject traversal and duplicates; emit only regular files. */
final class ArmBuildSource {
 static void tar(InputStream zip,Path destination)throws Exception {
  long total=0;int count=0;var names=new java.util.HashSet<String>();
  try(var in=new ZipInputStream(zip);var out=new TarArchiveOutputStream(Files.newOutputStream(destination))){
   out.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
   for(var entry=in.getNextEntry();entry!=null;entry=in.getNextEntry()){
    String name=entry.getName();if(++count>20000||name.length()>1024||name.startsWith("/")||name.contains("\\")||java.util.Arrays.asList(name.split("/")).contains("..")||!names.add(name))throw new IllegalArgumentException("소스 압축 경로 또는 파일 수가 잘못됐습니다.");
    if(entry.isDirectory()||java.util.Arrays.asList(name.split("/")).contains(".git")||name.endsWith("/.env")||name.equals(".env"))continue;
    byte[] bytes=in.readNBytes(16_777_217);total+=bytes.length;if(bytes.length>16_777_216||total>104_857_600)throw new IllegalArgumentException("소스 크기 한도를 넘었습니다.");
    var item=new TarArchiveEntry(name);item.setSize(bytes.length);item.setMode(name.endsWith(".sh")||name.endsWith("mvnw")||name.endsWith("gradlew")?0755:0644);out.putArchiveEntry(item);out.write(bytes);out.closeArchiveEntry();
   }
  }
 }
}
