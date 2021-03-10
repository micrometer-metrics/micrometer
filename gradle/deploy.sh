#!/bin/bash -e
# This script will build the project.

SWITCHES="-s --console=plain -x release -x artifactoryPublish -x bintrayUpload -x bintrayPublish"

if [ $CIRCLE_PR_NUMBER ]; then
  echo -e "WARN: Should not be here => Found Pull Request #$CIRCLE_PR_NUMBER => Branch [$CIRCLE_BRANCH]"
  echo -e "Not attempting to publish"
elif [ -z $CIRCLE_TAG ]; then
  echo -e "Publishing Snapshot => Branch ['$CIRCLE_BRANCH']"
  openssl aes-256-cbc -d -in gradle.properties.enc -out gradle.properties -k "$KEY" -md sha256
  ./gradlew snapshot publishNebulaPublicationToSnapshotRepository "$SWITCHES" -x test
elif [ $CIRCLE_TAG ]; then
  echo -e "Publishing Release => Branch ['$CIRCLE_BRANCH']  Tag ['$CIRCLE_TAG']"
  openssl aes-256-cbc -d -in gradle.properties.enc -out gradle.properties -k "$KEY" -md sha256
  case "$CIRCLE_TAG" in
  *-M\.*)
    ./gradlew -Prelease.disableGitChecks=true -Prelease.useLastTag=true candidate publishNebulaPublicationToMilestoneRepository "$SWITCHES"
    ;;
  *)
    ./gradlew -Prelease.disableGitChecks=true -Prelease.useLastTag=true final publishNebulaPublicationToMavenCentralRepository closeAndReleaseMavenCentralStagingRepository "$SWITCHES"
    ;;
  esac
else
  echo -e "WARN: Should not be here => Branch ['$CIRCLE_BRANCH']  Tag ['$CIRCLE_TAG']  Pull Request ['$CIRCLE_PR_NUMBER']"
  echo -e "Not attempting to publish"
fi

EXIT=$?

exit $EXIT
