package io.github.devmrko.companion.service;
/** A name collision is recoverable through the registered-service workflow. */
public final class ExistingDeploymentName extends IllegalStateException {
 private final String name;private final boolean registered;
 public ExistingDeploymentName(String name){this(name,true);}
 public ExistingDeploymentName(String name,boolean registered){super("이미 배포된 이름입니다. 등록 서비스에서 재배포하세요.");this.name=name;this.registered=registered;}
 public String href(){return !registered?"/services":"/services?selected="+java.net.URLEncoder.encode(name,java.nio.charset.StandardCharsets.UTF_8);}
}
