package io.github.devmrko.companion.error;
/** Carries an intentionally safe user-facing message; never raw cloud responses. */
public final class BusinessException extends RuntimeException {
 private final ErrorDescriptor descriptor;
 public BusinessException(ErrorDescriptor descriptor){super(descriptor.safeMessage());this.descriptor=descriptor;}
 public String code(){return descriptor.code();}
 public ErrorCategory category(){return descriptor.category();}
}
