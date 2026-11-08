package io.github.devmrko.companion.service;

import java.util.*;
import tools.jackson.databind.json.JsonMapper;

/** Private, version-pinned source input for the independent python-v1 runner. */
public final class PythonFunctionRequest {
    private PythonFunctionRequest() {}
    public static String request(String run,String object,String version,String sha256,String commit,
                                 String goal,String directory,String artifact,String requiredFreeBytes) {
        try {
            // Reuse common run/commit/goal/relative-path checks; no Git URL is sent.
            FunctionTemplate.request(run,"https://github.com/validation/only",commit,goal,"",directory,artifact);
            for(var value:List.of(object,version))
                if(value.isBlank() || value.length()>1024 || value.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException();
            if(!sha256.matches("[0-9a-f]{64}") || !requiredFreeBytes.matches("[1-9][0-9]{0,18}"))throw new IllegalArgumentException();
            long bytes=Long.parseLong(requiredFreeBytes);
            return JsonMapper.builder().build().writeValueAsString(Map.of(
                "action","build","runId",run,"commit",commit,"goal",goal,"directory",directory,"artifact",artifact,
                "requiredFreeBytes",bytes,"sourceBundle",Map.of("object",object,"version",version,"sha256",sha256)));
        } catch(Exception e) { throw Errors.INVALID.exception(); }
    }
}
