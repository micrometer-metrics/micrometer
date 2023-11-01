# Micrometer Application Metrics

[![Build Status](https://circleci.com/gh/micrometer-metrics/micrometer.svg?style=shield)](https://circleci.com/gh/micrometer-metrics/micrometer)
[![Apache 2.0](https://img.shields.io/github/license/micrometer-metrics/micrometer.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/io.micrometer/micrometer-core.svg)](https://search.maven.org/artifact/io.micrometer/micrometer-core)
[![Javadocs](https://www.javadoc.io/badge/io.micrometer/micrometer-core.svg)](https://www.javadoc.io/doc/io.micrometer/micrometer-core)
[![Revved up by Develocity](https://img.shields.io/badge/Revved%20up%20by-Develocity-06A0CE?logo=Gradle&labelColor=02303A)](https://ge.micrometer.io/)

An application metrics facade for the most popular monitoring tools. Instrument your code with dimensional metrics with a
vendor neutral interface and decide on the monitoring backend at the last minute.

More info and the user manual are available on [micrometer.io](https://micrometer.io).

Micrometer is the instrumentation library underpinning Spring Boot 2's or later metrics collection.

Micrometer artifacts work with Java 8 or later. We're releasing a [multi-release JAR](https://docs.oracle.com/javase/9/docs/specs/jar/jar.html#multi-release-jar-files) with support for JDK-version specific features (e.g. JDK 11's `HttpClient` instrumentation).

## Supported versions

See [Micrometer's support policy](https://micrometer.io/docs/support).

## Join the discussion

Join the [Micrometer Slack](https://slack.micrometer.io) to share your questions, concerns, and feature requests.

## Snapshot builds

Snapshots are published to `repo.spring.io` for every successful build on the `main` branch and maintenance branches.

To use:

```groovy
repositories {
    maven { url 'https://repo.spring.io/snapshot' }
}

dependencies {
    implementation 'io.micrometer:micrometer-core:latest.integration'
}
```

## Milestone releases

Milestone releases are published to https://repo.spring.io/milestone.
Include that as a maven repository in your build configuration to use milestone releases.
Note that milestone releases are for testing purposes and are not intended for production use.

## Documentation

The reference documentation is managed in [a separate GitHub repository](https://github.com/micrometer-metrics/micrometer-docs) and published to https://micrometer.io/.

## Contributing

See our [Contributing Guide](CONTRIBUTING.md) for information about contributing to Micrometer.

-------------------------------------
_Licensed under [Apache Software License 2.0](https://www.apache.org/licenses/LICENSE-2.0)_

_Sponsored by [VMware](https://tanzu.vmware.com)_
