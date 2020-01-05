# Overview

This is a poc using [Spring Boot] 2.2 and [Prometheus Push Gateway] with [Micrometer].

The Prometheus Pushgateway exists to allow ephemeral and batch jobs to expose their metrics to Prometheus. Since these kinds of jobs may not exist long enough to be scraped, they can instead push their metrics to a Pushgateway. The Pushgateway then exposes these metrics to Prometheus.

# Setup

## Prometheus Push Gateway

First, run the command bellow:

~~~
docker run -d --net=host --name=prometheus-pushgateway prom/pushgateway:latest
~~~

Second, open the browser and access the address `http://localhost:9091`

## Application

Start the application

~~~
mvn spring-boot:run
~~~

# Testing

First, call the endpoint `http:\\localhost:8080\hello`

Second, open the Prometheus Push Gateway `http:\\localhost:9091` and see the measurements, mainly the `hello-counter`

![alt text](https://github.com/larchanjo/poc-spring-prometheus-gateway/blob/master/src/main/resources/static/counter.png "Prometheus Push Gateway | Counter")

Third, **be happy**

[Spring Boot]: https://spring.io/blog/2019/10/16/spring-boot-2-2-0
[Prometheus Push Gateway]: https://github.com/prometheus/pushgateway
[Micrometer]: https://github.com/micrometer-metrics/micrometer

# Observations

This poc was based on this [pull request](https://github.com/spring-projects/spring-boot/pull/14353).