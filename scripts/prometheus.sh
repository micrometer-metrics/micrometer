#!/usr/bin/env bash
docker run -p 9090:9090 \
-v $(pwd)/prometheus.yml:/etc/prometheus/prometheus.yml \
prom/prometheus:v2.17.1
