#!/bin/sh
echo "##################################################"
echo "# IMPORTANT: Once per operating system restart, you must run 'sudo ifconfig lo0 alias 10.200.10.1/24' to allow Prometheus to scrape targets on the host"
echo "##################################################"
docker run -p 9090:9090 \
-v $(pwd)/prometheus.yml:/etc/prometheus/prometheus.yml \
prom/prometheus:v2.2.0
