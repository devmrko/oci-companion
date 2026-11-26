package io.github.devmrko.companion.adapter;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class OkeManagedPathTest {
 @TempDir Path root;
 @Test void parentAliasUsesSameRealDirectory()throws Exception{
  var real=Files.createDirectories(root.resolve("real/oke-kubeconfigs"));var alias=root.resolve("alias");Files.createSymbolicLink(alias,root.resolve("real"));var file=Files.writeString(real.resolve("oke-test.json"),"{}");
  assertEquals(file.toRealPath(),OciOkeWorkloadConnections.managedPath(real,alias.resolve("oke-kubeconfigs/oke-test.json").toString()));
  assertEquals(file.toRealPath(),OciOkeWorkloadConnections.managedPath(alias.resolve("oke-kubeconfigs"),file.toString()));
 }
 @Test void outsideDirectoryAndSymlinkFilesAreNeverAccepted()throws Exception{
  var managed=Files.createDirectory(root.resolve("oke-kubeconfigs"));var outside=Files.writeString(root.resolve("oke-outside.json"),"private");
  assertThrows(IllegalArgumentException.class,()->OciOkeWorkloadConnections.managedPath(managed,outside.toString()));
  var link=Files.createSymbolicLink(managed.resolve("oke-link.json"),outside);assertThrows(IllegalArgumentException.class,()->OciOkeWorkloadConnections.managedPath(managed,link.toString()));
  var rootLink=Files.createSymbolicLink(root.resolve("root-link"),managed);assertThrows(IllegalArgumentException.class,()->OciOkeWorkloadConnections.managedPath(rootLink,managed.resolve("oke-missing.json").toString()));
  assertEquals("private",Files.readString(outside));
 }
}
