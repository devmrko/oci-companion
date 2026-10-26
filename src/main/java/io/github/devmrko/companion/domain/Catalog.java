package io.github.devmrko.companion.domain;
import java.util.List;
public final class Catalog {
    private Catalog() {}
    public record Choice(String id,String name) {}
    public record Resource(String id,String name,String kind,String state,String url) {}
    public record Snapshot(int version,String scope,String updatedAt,List<Resource> resources) {}
    public record Source(String id,String provider,String name,String url,String branch,String resourceId) {}
    public record Sources(int version,String scope,List<Source> items) {}
    public record View(Snapshot snapshot,String error) {}
}
