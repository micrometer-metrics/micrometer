# Spring Metrics

[![Build Status](https://circleci.com/gh/spring-projects/spring-metrics.svg?style=svg)](https://circleci.com/gh/spring-projects/spring-metrics)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/spring-projects/spring-metrics?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)
[![Apache 2.0](https://img.shields.io/github/license/spring-projects/spring-metrics.svg)](http://www.apache.org/licenses/LICENSE-2.0)

Early work on dimensional Spring Metrics for application monitoring.

## Reference Documentation

You can view an early preview of the reference [documentation here](http://docs.spring.io/spring-metrics/docs/current/public). Lots more to come.

## Goals

In addition to the features already present in Spring Boot [Actuator Metrics](https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-metrics.html), these are some goals for inclusion in an initial release:

* Adopt a dimensional system rather than hierarchical. Many metrics backends are dimensional in nature. A hierarchical name can be derived consistently from a series of dimensions for those that are not (example of this in spectator-reg-metrics3).
* Reduce instrumentation cost (in terms of CPU/memory overhead).
* Support timers, gauges, counters, and distribution summaries as our meter primitives.
* Configurable clock source for better test support.
* Support tiering metrics by priority to allow for different push intervals (the relatively fewer critical metrics are published at a shorter interval than less critical metrics).
* Support dynamically determined dimension values.
    - Similarly, allow dynamically determined, high-cardinality dimensions to be turned off application wide by property.
* Logging appenders to track the number of log messages and stack traces being emitted from the app.
* Fold in buffer pool and memory pool metrics reported by the JDK via JMX.
* Fold in information about GC causes.

## Installing

Pre-release artifacts are being published frequently, but are NOT intended for production use.

In Gradle:

```groovy
compile 'org.springframework.metrics:spring-metrics:latest.release'
```

Or in Maven:

```xml
<dependency>
  <groupId>org.springframework.metrics</groupId>
  <artifactId>spring-metrics</artifactId>
  <version>${metrics.version}</version>
</dependency>
```

## Building Locally

Run `./gradlew pTML` to publish a snapshot to your Maven local repo. To consume:

```groovy
repositories {
    mavenLocal()
}

dependencies {
    compile 'org.springframework.metrics:spring-metrics:latest.integration'
}
```

## Instrumentation

*Defined:* The API used to create and interact with meter instances, generally through a meter registry. An individual meter holds one or more metrics depending on its type.

*Existing API:* `Metric`, ½ of `MetricWriter`, `CounterService`, `GaugeService`

Support for Prometheus and Spectator collectors is in development.

A TCK verifying collector implementation correctness is provided in `org.springframework.metrics.instrument`.

Support targets:

* Spectator
* Prometheus Java client
* Dropwizard Metrics (hierarchical)
* StatsD and Datadog StatsD

## Exporters

*Defined:* Exporters identify export-worthy metrics from a meter registry, transform (e.g. convert monotonically increasing counters to a rate), add global dimensions, and push metrics to a metrics backend on a periodic interval(s).

*Existing API:* `Exporter`, `@ExportMetricWriter`, `@ExportMetricReader`, `MetricCopyExporter`, `MetricReader`, `AggregateMetricReader`, ½ of `MetricWriter`

* servo-atlas
* Dropwizard Metrics "Reporters"
* Datadog StatsD (also acts as a collector)
* Direct to Datadog HTTP API
* JMX
* PCF Metrics

## Backends

*Defined:* A metrics backend is a time-series database. It may be a plain TSDB or contain optimizations for metrics specifically like:

* Tiered reduction in granularity of older metrics to save on storage
* Tiered persistence of metrics, prioritizing more recent metrics for fastest lookup and older metrics for cost
* Act like a circular buffer for time-series data to more strictly bound resource consumption

Metrics backends typically contain some metrics-specific query interface (e.g. PromQL, Atlas stack language). In some cases the metrics backend also happens to serve dashboards and alerting configuration that we are calling a metrics frontend. In other cases, these pieces are distinct.

Potential support targets:

* Atlas
* Prometheus (Pushgateway)
* Graphite
* InfluxDB
* Datadog
* OpenTSDB
* RRDtool?
* Ganglia
* Some supported statsd backends
* PCF Metrics
* JMX
* Message channel -- not a backend in itself of course, but could be linked to one indirectly?
* AWS CloudWatch
* Elasticsearch -- understand why projects like Orestes exist first

## Frontends

*Defined:* Frontends provide some combination of individual metrics graphs, whole dashboards, and alerting configuration.

Support targets:

* Grafana
* Graphite
* Prometheus
* RRDtool
* Atlas -- Requires prying the Atlas frontend out of closed source at Netflix. They also have two more closed source pieces cleverly named:
* Alerts
* Netflix Dashboards
