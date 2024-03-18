package io.github.indyaah.coreartifact.maven;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Named;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.repository.Authentication;
import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.execution.MavenExecutionRequest;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.GetAuthorizationTokenRequest;
import software.amazon.awssdk.services.codeartifact.model.GetAuthorizationTokenResponse;

@Named("codeartifact-token")
public class CodeArtifactTokenInjectingSpy extends AbstractEventSpy {

  private static final Pattern CODE_ARTIFACT_PATTERN =
      Pattern.compile(
          "([a-zA-Z-]+){1,63}-([0-9]{12})\\.d\\.codeartifact.([a-z]+-[a-z]+-[0-9])\\.amazonaws\\.com");
  private final Map<String, String> tokenCache = new ConcurrentHashMap<>();

  @Override
  public void onEvent(final Object event) throws Exception {
    if ((event instanceof MavenExecutionRequest)) {
      handleExecutionRequest((MavenExecutionRequest) event);
    }
    super.onEvent(event);
  }

  private void handleExecutionRequest(MavenExecutionRequest event) {
    final Properties systemProperties = event.getSystemProperties();
    final Properties userProperties = event.getUserProperties();
    final String username =
        resolveProperty("CODEARTIFACT_USERNAME", systemProperties, userProperties, "aws");

    final Map<String, String> repoServerIdToTokenMap =
        event.getRemoteRepositories().stream()
            .map(
                repo -> {
                  try {
                    URL url = new URL(repo.getUrl());
                    String host = url.getHost();
                    Matcher matcher = CODE_ARTIFACT_PATTERN.matcher(host);
                    if (matcher.matches()) {
                      final String region = matcher.group(3);
                      final String domain =
                          resolveProperty(
                              "CODEARTIFACT_DOMAIN",
                              systemProperties,
                              userProperties,
                              matcher.group(1));
                      final String owner =
                          resolveProperty(
                              "CODEARTIFACT_OWNER",
                              systemProperties,
                              userProperties,
                              matcher.group(2));
                      final String key = domain + owner + region;
                      final String token =
                          tokenCache.computeIfAbsent(
                              key,
                              s -> {
                                final CodeartifactClient codeartifactClient =
                                    CodeartifactClient.builder().region(Region.of(region)).build();
                                final GetAuthorizationTokenRequest tokenRequest =
                                    GetAuthorizationTokenRequest.builder()
                                        .domain(domain)
                                        .domainOwner(owner)
                                        .build();
                                final GetAuthorizationTokenResponse response =
                                    codeartifactClient.getAuthorizationToken(tokenRequest);
                                return response.authorizationToken();
                              });
                      repo.setAuthentication(new Authentication(username, token));
                      return new AbstractMap.SimpleEntry<>(repo.getId(), token);
                    }
                  } catch (MalformedURLException ignored) {
                  }
                  return null;
                })
            .filter(Objects::nonNull)
            .collect(
                Collectors.toMap(
                    AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));

    event.getServers().stream()
        .filter(server -> username.equalsIgnoreCase(server.getUsername()))
        .forEach(
            server -> {
              final String password = repoServerIdToTokenMap.get(server.getId());
              if (StringUtils.isNotBlank(password)) {
                server.setPassword(password);
              }
            });
  }

  private String resolveProperty(
      String propertyName, Properties system, Properties user, String defaultVal) {
    final String sysProp = system.getProperty(propertyName);
    final String sysPropPrefix = system.getProperty("env." + propertyName);
    final String userProp = user.getProperty(propertyName);

    if (isNotBlank(userProp)) return userProp;
    else if (isNotBlank(sysProp)) return sysProp;
    else if (isNotBlank(sysPropPrefix)) return sysPropPrefix;
    else return defaultVal;
  }
}
