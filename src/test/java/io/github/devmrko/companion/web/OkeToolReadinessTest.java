package io.github.devmrko.companion.web;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class OkeToolReadinessTest {
 @TempDir Path root;
 @Test void absoluteFileRequiresExecutePermission()throws Exception{
  var file=Files.writeString(root.resolve("tool"),"unused");file.toFile().setExecutable(false);
  assertFalse(OkeToolReadiness.check("tool",file.toString(),"VAR",false,"").available());
  assertTrue(file.toFile().setExecutable(true));
  assertTrue(OkeToolReadiness.check("tool",file.toString(),"VAR",false,"").available());
  assertFalse(OkeToolReadiness.check("tool",root.toString(),"VAR",false,"").available());
 }
 @Test void onlyOciUsesAbsolutePathEntries()throws Exception{
  var file=Files.writeString(root.resolve("oci"),"unused");assertTrue(file.toFile().setExecutable(true));
  assertTrue(OkeToolReadiness.check("OCI","oci","VAR",true,root.toString()).available());
  assertFalse(OkeToolReadiness.check("kubectl","oci","VAR",false,root.toString()).available());
  assertFalse(OkeToolReadiness.check("OCI","oci","VAR",true,".:relative:").available());
  assertFalse(OkeToolReadiness.check("OCI","missing","VAR",true,root.toString()).available());
 }
}
