package com.github.indyaah.coreartifact.maven;

import java.util.Properties;
import javax.inject.Named;
import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.execution.MavenExecutionRequest;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.GetAuthorizationTokenRequest;
import software.amazon.awssdk.services.codeartifact.model.GetAuthorizationTokenResponse;

@Named("codeartifact-token")
public class CodeArtifactTokenInjectingSpy extends AbstractEventSpy {

  private final CodeartifactClient codeartifactClient = CodeartifactClient.builder().build();

  @Override
  public void onEvent(final Object event) {
    if (!(event instanceof MavenExecutionRequest)) {
      return;
    }
    final MavenExecutionRequest request = (MavenExecutionRequest) event;
    final Properties systemProperties = request.getSystemProperties();

    final String username = systemProperties.getProperty("CODEARTIFACT_USERNAME", "aws");
    final String domain = systemProperties.getProperty("CODEARTIFACT_DOMAIN");
    final String owner = systemProperties.getProperty("CODEARTIFACT_OWNER");

    final GetAuthorizationTokenRequest tokenRequest =
        GetAuthorizationTokenRequest.builder().domain(domain).domainOwner(owner).build();
    final GetAuthorizationTokenResponse response =
        codeartifactClient.getAuthorizationToken(tokenRequest);
    final String token = response.authorizationToken();

    request.getServers().stream()
        .filter(server -> username.equalsIgnoreCase(server.getUsername()))
        .forEach(server -> server.setPassword(token));
  }
}
