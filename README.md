# Micrometer Application Metrics

[![Build Status](https://circleci.com/gh/spring-projects/spring-metrics.svg?style=svg)](https://circleci.com/gh/spring-projects/spring-metrics)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/spring-projects/spring-metrics?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)
[![Apache 2.0](https://img.shields.io/github/license/spring-projects/spring-metrics.svg)](http://www.apache.org/licenses/LICENSE-2.0)

An application metrics facade for the most popular monitoring tools. Instrument your code with dimensional metrics with a
vendor neutral interface and decide on the monitoring backend at the last minute.

Micrometer is the instrumentation library underpinning Spring Boot's metrics collection.

See the [reference documentation](http://docs.spring.io/spring-metrics/docs/current/public). Lots more to come.

## Supported monitoring backends

* Prometheus
* Netflix Atlas
* Datadog (directly via API)
* InfluxDB(coming)
* StatsD (coming)
* Graphite (coming)

## Features

Micrometer provides an interface for dimensional timers, gauges, counters, 
distribution summaries, and long task timers and an implementation of these 
interfaces for all the supported monitoring backends.

Additionally, it provides pre-configured sets of metrics for:

* Guava caches
* Executors, ExecutorService, and derivatives
* `ClassLoader`s
* Garbage collection
* CPU utilization
* Thread pools
* Logback level counts

For test support, Micrometer features a configurable clock source wherever the clock is used.

## Installing

Pre-release artifacts are being published frequently, but are NOT intended for production use.

In Gradle:

```groovy
compile 'io.micrometer:micrometer-core:latest.release'
```

Or in Maven:

```xml
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-core</artifactId>
  <version>${micrometer.version}</version>
</dependency>
```

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
