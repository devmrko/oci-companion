package io.github.devmrko.companion.service;
import java.util.*;
import io.github.devmrko.companion.port.OkeRuntimeResources.PullRegistry;
public final class PullRegistryHosts {
    private PullRegistryHosts() {}
    /** Return host names only; credential values are never returned, persisted or logged. */
    public static List<String> decode(byte[] encoded) throws Exception {
        if(encoded.length>65536)throw new IllegalArgumentException();
        byte[] decoded=Base64.getDecoder().decode(encoded);
        try {
            var auths=com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(decoded).path("auths");
            if(!auths.isObject()||auths.size()>32)throw new IllegalArgumentException();
            var hosts=new TreeSet<String>();
            auths.fieldNames().forEachRemaining(key->{
                String value=key.startsWith("https://")?key.substring(8):key;
                if(value.endsWith("/"))value=value.substring(0,value.length()-1);
                if(value.matches("[a-zA-Z0-9.-]+(?::[0-9]{1,5})?"))hosts.add(value.toLowerCase(Locale.ROOT));
            });return List.copyOf(hosts);
        } finally {Arrays.fill(decoded,(byte)0);Arrays.fill(encoded,(byte)0);}
    }
    public static String choose(String choice,String regionName,String regionKey,PullRegistry secret) {
        String named=regionName+".ocir.io",keyed=regionKey+".ocir.io";
        return switch(choice){case "REGION_NAME"->named;case "REGION_KEY"->keyed;case "AUTO"->secret!=null&&secret.available()?secret.hosts().stream().filter(h->h.equals(named)||h.equals(keyed)).findFirst().orElse(keyed):keyed;default->throw Errors.INVALID.exception();};
    }
}
