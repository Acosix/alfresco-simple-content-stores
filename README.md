[![Build Status](https://travis-ci.org/Acosix/alfresco-simple-content-stores.svg?branch=master)](https://travis-ci.org/Acosix/alfresco-simple-content-stores)

# About

This addon provides a set of simple / common content store implementations to enhance any installation of Alfresco Community or Enterprise. It also provides a mechanism that supports configuring custom content stores without any need for Spring bean definition / XML manipulation or overriding, just by using properties inside of the alfresco-global.properties file. General information about [general aspects](./docs/GeneralAspects.md) and [configuration approach](./docs/GeneralConfiguration.md) can be found in the specific documentation pages in the ``docs`` folder in this project.

## Compatibility

This module is built to be compatible with Alfresco 5.2 and above. Build and tests are done primarily against Alfresco 6.1, but the underlying APIs have been determined to have been stable since 5.2. The primary difference to be aware of between Alfresco 5.2 and 6.x or newer is the fact that since Alfresco 6.x, the Tukaani XZ already comes pre-bundled with Alfresco. Users of Alfresco 5.2 which want to use the compressing content store functionality of this module need to manually add this library to their installation, as it is no longer bundled with the AMP of this project starting with version 1.2.
This module may be used on either Community or Enterprise Edition. Separate branches for compatibility versions targeting Alfresco [4.2](https://github.com/Acosix/alfresco-simple-content-stores/tree/alfresco-4.2) and [5.0/5.1](https://github.com/Acosix/alfresco-simple-content-stores/tree/alfresco-5.0) were maintained only up to version 1.1 of this project - those Alfresco versions are now considered to be too old and rare in use to warrant any more (unpaid) support.

## Provided Stores

Currently, this addon provides:

- a simple [file store](./docs/StandardFileStore.md)
- a [tenant routing file store](./docs/TenantRoutingFileStore.md) (for backwards compatibility with Alfresco default unencrypted content store)
- a [site file store](./docs/SiteRoutingFileStore.md)
- a [site routing store](./docs/SiteRoutingStore.md)
- a [type routing store](./docs/TypeRoutingStore.md)
- a [tenant routing store](./docs/TenantRoutingStore.md)
- a [selector property-based routing store](./docs/SelectorPropertyRoutingStore.md)
- a [compressing store](./docs/CompressingStore.md)
- a [deduplicating store](./docs/DeduplicatingStore.md)
- an [encrypting store](./docs/EncryptingStore.md)
- a [caching store](./docs/CachingStore.md)
- an [aggregating store](./docs/AggregatingStore.md)
- a [dummy store](./docs/DummyStore.md)

## OOTBee Support Tools Command Console Plugin

In order to expose the runtime-administration capabilities specifically for the ecnrypting content store, this module includes a plugin extension to the [Command Console tool](https://github.com/OrderOfTheBee/ootbee-support-tools/wiki/Command-Console) of the [OOTBee Support Tools addon](https://github.com/OrderOfTheBee/ootbee-support-tools), available since version 1.1.0.0 of that addon. When both modules are installed, the administrative actions can be accessed by going to e.g. `<host>/alfresco/s/ootbee/admin/command-console` and then using the `activatePlugin simple-content-stores` command. Once activated, the following commands are available:

- `listEncryptionKeys <active|inactive>`
- `enableEncryptionKey <masterKey>`
- `disableEncryptionKey <masterKey>`
- `countEncryptedSymmetricKeys <masterKey>`
- `listEncryptionKeysEligibleForReEncryption`
- `reEncryptSymmetricKeys <masterKey>`

In order to have the plugin listed in the global command `listPlugins`, the property `ootbee-support-tools.command-console.plugins` must be set via Alfresco's global properties to include the value `simple-content-stores` in the comma-separated list of values. A future version of OOTBee Support Tools may improve discovery of plugins and not require this additional configuration, which needs to merge all the plugins from all installed modules.

# Build

This project uses a Maven build using templates from the [Acosix Alfresco Maven](https://github.com/Acosix/alfresco-maven) project and produces module AMPs, regular Java *classes* JARs, JavaDoc and source attachment JARs, as well as installable (Simple Alfresco Module) JAR artifacts for the Alfresco Content Services and Share extensions. If the installable JAR artifacts are used for installing this module, developers / users are advised to consult the 'Dependencies' section of this README.

## Maven toolchains

By inheritance from the Acosix Alfresco Maven framework, this project uses the [Maven Toolchains plugin](http://maven.apache.org/plugins/maven-toolchains-plugin/) to allow potential cross-compilation against different Java versions. This plugin is used to avoid potentially inconsistent compiler and library versions compared to when only the source/target compiler options of the Maven compiler plugin are set, which (as an example) has caused issues with some Alfresco releases in the past where Alfresco compiled for Java 7 using the Java 8 libraries.
In order to build the project it is necessary to provide a basic toolchain configuration via the user specific Maven configuration home (usually ~/.m2/). That file (toolchains.xml) only needs to list the path to a compatible JDK for the Java version required by this project, which currently is Java 8.

```xml
<?xml version='1.0' encoding='UTF-8'?>
<toolchains xmlns="http://maven.apache.org/TOOLCHAINS/1.1.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/TOOLCHAINS/1.1.0 http://maven.apache.org/xsd/toolchains-1.1.0.xsd">
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>1.8</version>
      <vendor>oracle</vendor>
    </provides>
    <configuration>
      <jdkHome>C:\Program Files\Java\jdk1.8.0_112</jdkHome>
    </configuration>
  </toolchain>
</toolchains>
```

## Docker-based integration tests

In a default build using ```mvn clean install```, this project will build the extension for Alfresco Content Services, executing regular unit-tests without running integration tests. The integration tests of this project are based on Docker and require a Docker engine to run the necessary components (PostgreSQL database as well as Alfresco Content Services). Since a Docker engine may not be available in all environments of interested community members / collaborators, the integration tests have been made optional. A full build, including integration tests, can be run by executing

```text
mvn clean install -Ddocker.tests.enabled=true
```

Due to OS- and file system specific permission handling, as well as the use of Docker mounted directories to access the Alfresco content store from within unit test classes, the integration tests may also not work on systems with strict file system security in place. The project is currently being developed in a Microsoft Windows environment, and integration tests should work without fail in that environment, while *nix-based environments have been reported as problematic. Acosix will aim to work on finding alternative approaches to verifying files in the content store in future versions to enable integration tests to be run on any environment.

## Dependencies

This module depends on the following projects / libraries:

- [Tukaani XZ for Java](https://tukaani.org/xz/java.html) (Public Domain)
- Acosix Alfresco Utility (Apache License, Version 2.0) - core extension

Tukaani XZ is already part of the ACS distribution since Alfresco 6.0 - it was included in AMPs of this project up to version 1.1, but has since then been removed to avoid conflicts / overrides with the ACS-bundled version of the library. If the compressing content store is to be used, this library must be manually added to an installation of Alfresco 5.2.
The Acosix Alfresco Utility project provides the core extension for Alfresco Content Services as a separate artifact from the full module, which needs to be installed in Alfresco Content Services before the AMP of this project can be installed.

When the installable JAR produced by the build of this project is used for installation, the developer / user is responsible to either manually install all the required components / libraries provided by the listed projects, or use a build system to collect all relevant direct / transitive dependencies.
**Note**: The Acosix Alfresco Utility project is also built using templates from the Acosix Alfresco Maven project, and as such produces similar artifacts. Automatic resolution and collection of (transitive) dependencies using Maven / Gradle will resolve the Java *classes* JAR as a dependency, and **not** the installable (Simple Alfresco Module) variant. It is recommended to exclude Acosix Alfresco Utility from transitive resolution and instead include it directly / explicitly.

## Using SNAPSHOT builds

In order to use a pre-built SNAPSHOT artifact published to the Open Source Sonatype Repository Hosting site, the artifact repository may need to be added to the POM, global settings.xml or an artifact repository proxy server. The following is the XML snippet for inclusion in a POM file.

```xml
<repositories>
    <repository>
        <id>ossrh</id>
        <url>https://oss.sonatype.org/content/repositories/snapshots</url>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>
```