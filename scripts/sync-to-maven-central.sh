#!/usr/bin/env bash
set -e

if [[ -z "$VERSION" ]]
then
    echo "\$VERSION must be set."
    exit -1
else
    echo "Syncing version $VERSION"
fi

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
    micrometer-spring-legacy
    micrometer-jersey2
    micrometer-registry-stackdriver
    micrometer-registry-elastic
    micrometer-registry-kairos
    micrometer-registry-dynatrace
    micrometer-registry-humio
    micrometer-registry-azure-monitor
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
			POST "https://api.bintray.com/maven_central_sync/spring/jars/${module}/versions/${VERSION}" > /dev/null || { echo "Failed to sync" >&2; exit 1; }
    echo "  complete"
done