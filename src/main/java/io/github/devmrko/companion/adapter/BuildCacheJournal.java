package io.github.devmrko.companion.adapter;
import io.github.devmrko.companion.port.BuildSpecDraftStore;
import java.time.*;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;
/** Only positively observed ownership is eligible; missing history never becomes an age estimate. */
final class BuildCacheJournal {
 record Entry(String digest,String tag,String lock,String kind,String createdAt,String lastUsed,boolean owned){}
 record Use(String repository,String lock,Map<String,String> images,Map<String,String> tags,Set<String> created){}
 record State(List<Entry> entries,boolean automatic){State {entries=List.copyOf(entries);}}
 private final BuildSpecDraftStore store;private final BuildExecutionLocks locks;private final JsonMapper json=JsonMapper.builder().build();
 BuildCacheJournal(BuildSpecDraftStore s,BuildExecutionLocks l){store=s;locks=l;}
 String key(String tenancy,String region,String repository){return "build-cache-v1\n"+tenancy+"\n"+region+"\n"+repository;}
 State read(String key){return store.read(key).map(d->json.readValue(d.content(),State.class)).orElse(new State(List.of(),false));}
 void save(String key,State state){store.save(key,new BuildSpecDraftStore.Draft("v1",json.writeValueAsString(state),Instant.now().toString()));}
 void record(String key,Use use,Instant now){
  if(!use.lock().matches("[a-f0-9]{64}")||!use.images().keySet().equals(Set.of("deps","runtime")))throw new IllegalArgumentException();
  for(var kind:use.images().keySet())if(!use.images().get(kind).matches("sha256:[a-f0-9]{64}")||!use.tags().getOrDefault(kind,"").matches(kind+"-[a-f0-9]{64}"))throw new IllegalArgumentException();
  try(var gate=locks.guard()){var state=read(key);var entries=new ArrayList<>(state.entries());for(var kind:use.images().keySet()){String digest=use.images().get(kind);var old=entries.stream().filter(e->e.digest().equals(digest)).findFirst();entries.removeIf(e->e.digest().equals(digest));boolean owned=old.map(Entry::owned).orElse(false)||use.created().contains(digest);entries.add(new Entry(digest,use.tags().get(kind),use.lock(),kind,old.map(Entry::createdAt).orElse(now.toString()),now.toString(),owned));}save(key,new State(entries,state.automatic()));}
 }
 static Set<String> eligible(State state,Instant now){
  var latest=new HashMap<String,Instant>();for(var e:state.entries())latest.merge(e.lock(),Instant.parse(e.lastUsed()),(a,b)->a.isAfter(b)?a:b);
  var retained=latest.entrySet().stream().sorted(Map.Entry.<String,Instant>comparingByValue().reversed()).limit(3).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
  var candidates=new HashSet<String>();for(var e:state.entries())if(e.owned()&&!retained.contains(e.lock())&&Instant.parse(e.lastUsed()).isBefore(now.minus(Duration.ofDays(30)))&&Instant.parse(e.createdAt()).isBefore(now.minus(Duration.ofDays(7))))candidates.add(e.digest());return candidates;
 }
}
