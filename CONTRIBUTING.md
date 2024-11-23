# Contributing Guide

This Contributing Guide is intended for those that would like to contribute to Micrometer.

If you would like to use any of the published Micrometer modules as a library in your project, you can instead include the Micrometer artifacts from the Maven Central repository using your build tool of choice.

## Code of Conduct

See [our Contributor Code of Conduct](https://github.com/micrometer-metrics/.github/blob/main/CODE_OF_CONDUCT.md).

## Contributions

Contributions come in various forms and are not limited to code changes.
The Micrometer community benefits from contributions in all forms.

For example, those with Micrometer knowledge and experience can contribute by: 
* [Contributing documentation](https://github.com/micrometer-metrics/micrometer/tree/main/docs/)
* Answering [Stackoverflow questions](https://stackoverflow.com/tags/micrometer)
* Answering questions on the [Micrometer slack](https://slack.micrometer.io)
* Share Micrometer knowledge in other ways (e.g. presentations, blogs)

The remainder of this document will focus on guidance for contributing code changes. It will help contributors to build, modify, or test the Micrometer source code.

## Contributor License Agreement

Contributions in the form of source changes require that you fill out and submit the [Contributor License Agreement](https://cla.pivotal.io/sign/pivotal) if you have not done so previously.

## Getting the source

The Micrometer source code is hosted on GitHub at https://github.com/micrometer-metrics/micrometer.
You can use a Git client to clone the source code to your local machine.

## Building

Micrometer targets Java 8 but requires JDK 11 or later to build.
If you are not running Gradle with JDK 11 or later and Gradle cannot detect an existing JDK 17 installation, it will download one.
If you want to build the reference docs, you need to use JDK 17 or later.

The Gradle wrapper is provided and should be used for building with a consistent version of Gradle.

The wrapper can be used with a command, for example, `./gradlew` to build the project and check conventions.

## Importing into an IDE

This repository should be imported as a Gradle project into your IDE of choice.

## Code formatting

The [spring-javaformat plugin](https://github.com/spring-io/spring-javaformat) is configured to check and apply consistent formatting in the codebase through the build.
The `checkFormat` task checks the formatting as part of the `check` task.
Apply formatting with the `format` task.
You should rely on the formatting the `format` task applies instead of your IDE's configured formatting.

## Testing changes locally

Specific modules or a test class can be run from your IDE for convenience.

The Gradle `check` task depends on the `test` task, and so tests will be run as part of a build as described previously.

### Publishing local snapshots

Run `./gradlew pTML` to publish a Maven-style snapshot to your Maven local repo.
The build automatically calculates the "next" version for you when publishing snapshots.

These local snapshots can be used in another project to test the changes. For example:

```groovy
repositories {
    mavenLocal()
}

dependencies {
    implementation 'io.micrometer:micrometer-core:latest.integration'
}
```

## Running Docker integration tests

Micrometer has a number of integration tests that are implemented with [Testcontainers](https://www.testcontainers.org/) and use Docker. These tests do not run by default.

The integration tests for Elasticsearch use the elasticsearch docker image that has the [Elastic License 2.0](https://github.com/elastic/elasticsearch/blob/master/licenses/ELASTIC-LICENSE-2.0.txt).
Do not run the Micrometer integration tests if you do not agree to the terms of the license.

You need a Docker daemon running for these tests to work. You can run them with the `dockerTest` task. For example, with the Gradle wrapper: `./gradlew dockerTest`.
