#!/usr/bin/env bash
docker run -p 9090:9090 \
-v $(pwd)/prometheus.yml:/etc/prometheus/prometheus.yml \
-v $(pwd)/prometheus_rules.yml:/etc/prometheus/prometheus_rules.yml \
prom/prometheus:v2.2.0