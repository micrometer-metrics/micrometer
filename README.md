# Micrometer Docs Build

You're currently viewing the Antora playbook branch.
The playbook branch hosts the docs build that is used to build and publish the production docs site.

The Micrometer reference docs are built using https://antora.org[Antora].
This README covers how to build the docs in a software branch as well as how to build the production docs site locally.

## Building the Site

You can build the entire site by invoking the following and then viewing the site at `build/site/index.html`

[source,bash]
----
./gradlew antora
----

## Building a Specific Branch

You can build a specific branch and then viewing the branch specific site at `build/site/index.html`.

[source,bash]
----
./gradlew antora
----
