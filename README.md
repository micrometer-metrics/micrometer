# Micrometer Application Metrics

[![Build Status](https://circleci.com/gh/micrometer-metrics/micrometer.svg?style=shield)](https://circleci.com/gh/micrometer-metrics/micrometer)
[![Apache 2.0](https://img.shields.io/github/license/micrometer-metrics/micrometer.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/io.micrometer/micrometer-core.svg)](https://mvnrepository.com/artifact/io.micrometer/micrometer-core)
[![Javadocs](https://www.javadoc.io/badge/io.micrometer/micrometer-core.svg)](https://www.javadoc.io/doc/io.micrometer/micrometer-core)
[![Revved up by Gradle Enterprise](https://img.shields.io/badge/Revved%20up%20by-Gradle%20Enterprise-06A0CE?logo=Gradle&labelColor=02303A)](https://ge.spring.io/scans?search.rootProjectNames=micrometer)

An application metrics facade for the most popular monitoring tools. Instrument your code with dimensional metrics with a
vendor neutral interface and decide on the monitoring backend at the last minute.

More info and the user manual are available on [micrometer.io](https://micrometer.io).

Micrometer is the instrumentation library underpinning Spring Boot 2.0's metrics collection.

## Long-term support versions

See [Micrometer's support policy](https://micrometer.io/docs/support) for more details about long-term support (LTS) versus non-LTS versions.

| Minor version line | LTS | Final patch |
| ------------------ | --- | ----------- |
| `1.0.x`            | Yes | `1.0.11`    |
| `1.1.x`            | Yes |  |
| `1.2.x`            | No  | `1.2.2` |
| `1.3.x`            | Yes |  |
| `1.4.x`            | No  |  |
| `1.5.x`            | Yes |  |

## Join the discussion

Join the [Micrometer Slack](https://slack.micrometer.io) to share your questions, concerns, and feature requests.

## Snapshot builds

Snapshots are published to `repo.spring.io` for every successful build on the `master` branch and maintenance branches.

To use:

```groovy
repositories {
    maven { url 'https://repo.spring.io/libs-snapshot' }
}

dependencies {
    compile 'io.micrometer:micrometer-core:latest.integration'
}
```

## Building Locally

Run `./gradlew clean pTML` to publish a Maven-style snapshot to your Maven local repo. To consume:

```groovy
repositories {
    mavenLocal()
}

dependencies {
    compile 'io.micrometer:micrometer-core:latest.integration'
}
```

The build automatically calculates the "next" version for you when publishing snapshots.

## Documentation

The reference documentation is managed in [a separate GitHub repository](https://github.com/micrometer-metrics/micrometer-docs).

-------------------------------------
_Licensed under [Apache Software License 2.0](https://www.apache.org/licenses/LICENSE-2.0)_

_Sponsored by [VMware](https://tanzu.vmware.com)_
