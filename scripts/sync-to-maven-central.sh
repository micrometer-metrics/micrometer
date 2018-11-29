#!/usr/bin/env bash
set -e

MODULES=(
    micrometer-core
    micrometer-test
    micrometer-registry-atlas
    micrometer-registry-cloudwatch
    micrometer-registry-datadog
    micrometer-registry-ganglia
    micrometer-registry-graphite
    micrometer-registry-influx
    micrometer-registry-jmx
    micrometer-registry-new-relic
    micrometer-registry-prometheus
    micrometer-registry-signalfx
    micrometer-registry-statsd
    micrometer-registry-wavefront
    micrometer-registry-spring-legacy
    micrometer-jersey2
    micrometer-registry-stackdriver
    micrometer-registry-elastic
    micrometer-registry-kairos
    micrometer-registry-dynatrace
    micrometer-registry-humio
    micrometer-registry-azuremonitor
    micrometer-registry-appoptics
)

for module in "${MODULES[@]}"
do
   :
   echo "Syncing ${module}"
   curl \
			-s \
			--connect-timeout 240 \
			--max-time 2700 \
			-u ${BINTRAY_USERNAME}:${BINTRAY_API_KEY} \
			-H "Content-Type: application/json" -d "{\"username\": \"${SONATYPE_USER_TOKEN}\", \"password\": \"${SONATYPE_PASSWORD_TOKEN}\"}" \
			-f \
			-X \
			POST "https://api.bintray.com/maven_central_sync/spring/jars/micrometer-registry-statsd/versions/${VERSION}" > /dev/null || { echo "Failed to sync" >&2; exit 1; }
    echo "  complete"
done