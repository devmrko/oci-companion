package io.github.devmrko.companion.adapter;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class JGitSourcePushTest {
 @TempDir Path root;
 @Test void newBranchKeepsInitialCommitAndExistingBranchCannotBeOverwritten()throws Exception{
  try(var source=Git.init().setDirectory(root.resolve("source").toFile()).call();var target=Git.init().setDirectory(root.resolve("target").toFile()).call()){
   Files.writeString(root.resolve("source/app.txt"),"source");source.add().addFilepattern(".").call();var commit=source.commit().setMessage("source").setAuthor("test","test@localhost").call();
   Files.writeString(root.resolve("target/README"),"initial");target.add().addFilepattern(".").call();var initial=target.commit().setMessage("initial").setAuthor("test","test@localhost").call();
   String url=root.resolve("target/.git").toUri().toString();assertEquals(commit.name(),JGitSourcePush.pushNew(source,url,"companion/import",commit,null));
   assertEquals(initial.name(),target.getRepository().resolve("HEAD").name());assertEquals(commit.name(),target.getRepository().resolve("refs/heads/companion/import").name());
   assertThrows(RuntimeException.class,()->JGitSourcePush.pushNew(source,url,"companion/import",commit,null));assertEquals(initial.name(),target.getRepository().resolve("HEAD").name());
  }
 }
}
