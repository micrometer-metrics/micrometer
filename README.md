# Micrometer Application Metrics

[![Build Status](https://circleci.com/gh/micrometer-metrics/micrometer.svg?style=svg)](https://circleci.com/gh/micrometer-metrics/micrometer)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/micrometer-metrics/micrometer?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)
[![Apache 2.0](https://img.shields.io/github/license/micrometer-metrics/micrometer.svg)](http://www.apache.org/licenses/LICENSE-2.0)

An application metrics facade for the most popular monitoring tools. Instrument your code with dimensional metrics with a
vendor neutral interface and decide on the monitoring backend at the last minute.

More info and the user manual are available on [micrometer.io](http://micrometer.io).

Micrometer is the instrumentation library underpinning Spring Boot's metrics collection.

## Join the discussion

Join the [Micrometer Slack](http://slack.micromter.io) to share your questions, concerns, and feature requests.

## Building Locally

Run `./gradlew pTML` to publish a snapshot to your Maven local repo. To consume:

```groovy
repositories {
    mavenLocal()
}

dependencies {
    compile 'io.micrometer:micrometer-core:latest.integration'
}
```
