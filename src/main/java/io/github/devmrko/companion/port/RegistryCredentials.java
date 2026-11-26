package io.github.devmrko.companion.port;

import java.util.Arrays;

/** Invocation-only secret, never a view model or persistence DTO. */
public final class RegistryCredentials implements AutoCloseable {
 private final String username;
 private final char[] password;
 public RegistryCredentials(String username,char[] password){this.username=username;this.password=password.clone();}
 public String username(){return username;}
 public char[] password(){return password.clone();}
 @Override public String toString(){return "RegistryCredentials[redacted]";}
 @Override public void close(){Arrays.fill(password,'\0');}
}
