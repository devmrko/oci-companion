package io.github.devmrko.companion.service;
import io.github.devmrko.companion.port.BuildProjectResources.LogReadiness;
public final class BuildLogChecks {
    private BuildLogChecks() {}
    public static void requireReady(LogReadiness state) {
        if(state==LogReadiness.READY) return;
        if(state==LogReadiness.DISABLED) throw Errors.BUILD_LOGS_REQUIRED.exception();
        if(state==LogReadiness.NOT_FOUND) throw Errors.BUILD_LOGS_NOT_FOUND.exception();
        throw Errors.BUILD_LOGS_UNVERIFIED.exception();
    }
}
