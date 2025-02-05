#!/bin/bash -e
# This script will build the project.
SWITCHES="-s --console=plain -x test"
# circleci does not like multi-line values so they are base64 encoded
ORG_GRADLE_PROJECT_SIGNING_KEY="$(echo "$ORG_GRADLE_PROJECT_SIGNING_KEY" | base64 -d)"

if [ $CIRCLE_PR_NUMBER ]; then
  echo -e "WARN: Should not be here => Found Pull Request #$CIRCLE_PR_NUMBER => Branch [$CIRCLE_BRANCH]"
  echo -e "Not attempting to publish"
elif [ -z $CIRCLE_TAG ]; then
  echo -e "Publishing Snapshot => Branch ['$CIRCLE_BRANCH']"
  ./gradlew -Prelease.stage=SNAPSHOT snapshot publishNebulaPublicationToSnapshotRepository $SWITCHES
elif [ $CIRCLE_TAG ]; then
  echo -e "Publishing Release => Branch ['$CIRCLE_BRANCH'] Tag ['$CIRCLE_TAG']"
  case "$CIRCLE_TAG" in
  *-M*)
    ./gradlew -Prelease.disableGitChecks=true -Prelease.useLastTag=true -Prelease.stage=milestone candidate publishNebulaPublicationToMavenCentralRepository closeAndReleaseMavenCentralStagingRepository $SWITCHES
    ;;
  *-RC*)
     # -Prelease.stage=milestone instead of rc (should be rc), probably related to this bug: https://github.com/nebula-plugins/nebula-release-plugin/issues/213
    ./gradlew -Prelease.disableGitChecks=true -Prelease.useLastTag=true -Prelease.stage=milestone candidate publishNebulaPublicationToMavenCentralRepository closeAndReleaseMavenCentralStagingRepository $SWITCHES
    ;;
  *)
    ./gradlew -Prelease.disableGitChecks=true -Prelease.useLastTag=true -Prelease.stage=final final publishNebulaPublicationToMavenCentralRepository closeAndReleaseMavenCentralStagingRepository $SWITCHES
    ;;
  esac
else
  echo -e "WARN: Should not be here => Branch ['$CIRCLE_BRANCH'] Tag ['$CIRCLE_TAG'] Pull Request ['$CIRCLE_PR_NUMBER']"
  echo -e "Not attempting to publish"
fi

EXIT=$?

exit $EXIT
