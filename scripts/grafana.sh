#!/bin/sh
docker run -i -p 3000:3000 \
-v $(pwd)/grafana-datasource.yml:/etc/grafana/provisioning/datasources/grafana-datasource.yml \
grafana/grafana:6.7.2
