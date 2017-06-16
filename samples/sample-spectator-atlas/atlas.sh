#!/usr/bin/env bash

ATLAS_VERSION=1.5.3

if [ ! -f .atlas/memory.conf ]; then
    echo "Downloading Atlas in-memory configuration"
    mkdir .atlas
    curl -Lo .atlas/memory.conf https://raw.githubusercontent.com/Netflix/atlas/v1.5.x/conf/memory.conf
fi

if [ ! -f .atlas/atlas-$ATLAS_VERSION-standalone.jar ]; then
    echo "Downloading Atlas $ATLAS_VERSION standalone"
    curl -Lo .atlas/atlas-$ATLAS_VERSION-standalone.jar https://github.com/Netflix/atlas/releases/download/v$ATLAS_VERSION/atlas-$ATLAS_VERSION-standalone.jar
fi

java -jar .atlas/atlas-$ATLAS_VERSION-standalone.jar .atlas/memory.conf