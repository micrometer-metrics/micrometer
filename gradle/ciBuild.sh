#!/bin/bash
# This script will build the project.

SWITCHES="-s --console=plain"

if [ $CIRCLE_PR_NUMBER ]; then
  echo -e "Build Pull Request #$CIRCLE_PR_NUMBER => Branch [$CIRCLE_BRANCH]"
  ./gradlew clean build $SWITCHES
elif [ -z $CIRCLE_TAG ]; then
  echo -e ?'Build Branch with Snapshot => Branch ['$CIRCLE_BRANCH']'
  ./gradlew clean build $SWITCHES
elif [ $CIRCLE_TAG ]; then
  echo -e 'Build Branch for Release => Branch ['$CIRCLE_BRANCH']  Tag ['$CIRCLE_TAG']'
  case "$CIRCLE_TAG" in
  *-rc\.*)
    ./gradlew -Prelease.disableGitChecks=true -Prelease.useLastTag=true clean build candidate $SWITCHES
    ;;
  *)
    ./gradlew -Prelease.disableGitChecks=true -Prelease.useLastTag=true clean build final $SWITCHES
    ;;
  esac
else
  echo -e 'WARN: Should not be here => Branch ['$CIRCLE_BRANCH']  Tag ['$CIRCLE_TAG']  Pull Request ['$CIRCLE_PR_NUMBER']'
  ./gradlew clean build $SWITCHES
fi

EXIT=$?

exit $EXIT