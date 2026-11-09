package io.github.devmrko.companion.error;
public interface ErrorDescriptor {
 String code();
 String safeMessage();
 ErrorCategory category();
}
