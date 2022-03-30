# codeartifact-maven-extension

## Problem Statement

Current [recommended flow](https://docs.aws.amazon.com/codeartifact/latest/ug/maven-mvn.html) for
using CodeArtifact as maven repository is to export authentication token into your environment and
use that environment variable as part of user setting.xml (generally at `$M2_HOME/settings.xml`)

This creates a couple of problems;

1. Engineers have to keep exporting the token into their environment every 12 hours.
2. IDEs (at least IntelliJ) cant resolve maven dependency and keep showing annoying pop-up.

## Solution

The goal of this extension is to allow
injecting [CodeArtifact Auth token](https://docs.aws.amazon.com/codeartifact/latest/ug/tokens-authentication.html)
into maven reactor and override values coming from `$M2_HOME/settings.xml`.

## Notes

The implementation is (intentionally) quite brittle and simple.

When `MavenExecutionRequest` is fired in the build reactor; we intercept it and generate a token
using AWS java SDK. For doing that we rely on following system properties;

1. `CODEARTIFACT_USERNAME` defaults to `aws`
2. `CODEARTIFACT_DOMAIN` defaults to domain derived from CodeArtifact URL. e.g. if URL is https://my_domain-111122223333.d.codeartifact.us-west-2.amazonaws.com/maven/my_repo/ domain is derived as `my_domain`
3. `CODEARTIFACT_OWNER` defaults to account id derived from CodeArtifact URL.  e.g. if URL is https://my_domain-111122223333.d.codeartifact.us-west-2.amazonaws.com/maven/my_repo/ owner is derived as `111122223333`

The extension will generate a token for given code artifact domain and owner (account id). Any
servers in the reactor that are using `CODEARTIFACT_USERNAME`'s value as username would have their
password overridden dynamically with the geneated token value.

All system properties could be passed from `<properties>` block in your root pom.xml or via CLI (
e.g `-DCODEARTIFACT_DOMAIN="xxx`)

The underlying AWS client uses default provider chain, which will allow you to override AWS profile
being used by passing in `-Daws.profile` property (or setting `AWS_PROFILE` env var)
