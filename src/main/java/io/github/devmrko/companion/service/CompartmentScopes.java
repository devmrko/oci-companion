package io.github.devmrko.companion.service;
import io.github.devmrko.companion.domain.Catalog.Choice;
import java.util.*;
public final class CompartmentScopes {
    private CompartmentScopes() {}
    public static List<Choice> targets(List<Choice> choices,String selected,String mode) {
        if (!Set.of("selected","subtree","all").contains(mode) || choices.stream().noneMatch(c -> c.id().equals(selected))) throw Errors.INVALID.exception();
        if (mode.equals("all")) return List.copyOf(choices);
        var ids=new HashSet<String>();ids.add(selected);
        if (mode.equals("subtree")) {
            boolean changed;
            do { changed=false;for (var c:choices) if (ids.contains(c.parentId()) && ids.add(c.id())) changed=true; } while(changed);
        }
        return choices.stream().filter(c -> ids.contains(c.id())).toList();
    }
}
