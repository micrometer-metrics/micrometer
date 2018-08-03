#!/bin/sh
docker run -i -p 3000:3000 \
-v $(pwd)/grafana-datasource.yml:/etc/grafana/provisioning/datasources/grafana-datasource.yml \
-v $(pwd)/grafana-dashboard.yml:/etc/grafana/provisioning/dashboards/grafana-dashboard.yml \
-v $(pwd)/jvmgc-dashboard.json:/etc/grafana/dashboards/jvmgc.json \
-v $(pwd)/latency-dashboard.json:/etc/grafana/dashboards/latency.json \
-v $(pwd)/processor-dashboard.json:/etc/grafana/dashboards/processor.json \
grafana/grafana:5.1.0

