package com.github.indyaah.coreartifact.maven;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

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
    final Properties userProperties = request.getUserProperties();

    final String username =
        resolveProperty("CODEARTIFACT_USERNAME", systemProperties, userProperties, "aws");
    final String domain =
        resolveProperty("CODEARTIFACT_DOMAIN", systemProperties, userProperties, null);
    final String owner =
        resolveProperty("CODEARTIFACT_OWNER", systemProperties, userProperties, null);

    final GetAuthorizationTokenRequest tokenRequest =
        GetAuthorizationTokenRequest.builder().domain(domain).domainOwner(owner).build();
    final GetAuthorizationTokenResponse response =
        codeartifactClient.getAuthorizationToken(tokenRequest);
    final String token = response.authorizationToken();

    request.getServers().stream()
        .filter(server -> username.equalsIgnoreCase(server.getUsername()))
        .forEach(server -> server.setPassword(token));
  }

  private String resolveProperty(
      String propertyName, Properties system, Properties user, String defaultVal) {
    final String sysProp = system.getProperty(propertyName);
    final String sysPropPrefix = system.getProperty("env." + propertyName);
    final String userProp = user.getProperty(propertyName);

    if (isNotBlank(userProp)) return userProp;
    else if (isNotBlank(sysProp)) return sysProp;
    else if (isNotBlank(sysPropPrefix)) return sysProp;
    else return defaultVal;
  }
}
