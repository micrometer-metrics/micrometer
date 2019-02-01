# Micrometer Application Metrics

[![Build Status](https://circleci.com/gh/micrometer-metrics/micrometer.svg?style=shield)](https://circleci.com/gh/micrometer-metrics/micrometer)
[![Apache 2.0](https://img.shields.io/github/license/micrometer-metrics/micrometer.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/io.micrometer/micrometer-core.svg)](https://mvnrepository.com/artifact/io.micrometer/micrometer-core)
[![Javadocs](http://www.javadoc.io/badge/io.micrometer/micrometer-core.svg)](http://www.javadoc.io/doc/io.micrometer/micrometer-core)

An application metrics facade for the most popular monitoring tools. Instrument your code with dimensional metrics with a
vendor neutral interface and decide on the monitoring backend at the last minute.

More info and the user manual are available on [micrometer.io](https://micrometer.io).

Micrometer is the instrumentation library underpinning Spring Boot 2.0's metrics collection.

## Join the discussion

Join the [Micrometer Slack](http://slack.micrometer.io) to share your questions, concerns, and feature requests.

## Snapshot builds

Snapshots are published to `repo.spring.io` for every successful build on the master branch.

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

-------------------------------------
_Licensed under [Apache Software License 2.0](https://www.apache.org/licenses/LICENSE-2.0)_

_Sponsored by [Pivotal](https://pivotal.io)_
