# Spring Metrics

Early work on dimensional Spring Metrics for application monitoring.

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

## Collectors

Support for Prometheus and Spectator collectors is in development.

A TCK verifying collector implementation correctness is provided in `org.springframework.metrics.tck`.

## Exporters

Nothing yet!

## Backends

Nothing yet!

## Frontends

Nothing yet!