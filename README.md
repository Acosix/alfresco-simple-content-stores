[![Build Status](https://travis-ci.org/Acosix/alfresco-simple-content-stores.svg?branch=master)](https://travis-ci.org/Acosix/alfresco-simple-content-stores)

# About

This addon provides a set of simple / common content store implementations to enhance any installation of Alfresco Community or Enterprise. It also provides a mechanism that supports configuring custom content stores without any need for Spring bean definition / XML manipulation or overriding, just by using properties inside of the alfresco-global.properties file.

## Compatibility

This module is built to be compatible with Alfresco 5.2 and above. It may be used on either Community or Enterprise Edition. Separate branches exist for compatibility versions targeting Alfresco [4.2](https://github.com/Acosix/alfresco-simple-content-stores/tree/alfresco-4.2) and [5.0/5.1](https://github.com/Acosix/alfresco-simple-content-stores/tree/alfresco-5.0).

## Provided Stores

The [wiki](https://github.com/Acosix/alfresco-simple-content-stores/wiki) contains detailed information about all the stores this addon provides, as well as their configuration properties and configuration examples. Currently, this addon provides:

- a simple [file store](./docs/StandardFileStore.md)
- a [tenant routing file store](./docs/TenantRoutingFileStore.md) (for backwards compatibility with Alfresco default unencrypted content store)
- a [site file store](./docs/SiteRoutingFileStore.md)
- a [site routing store](./docs/SiteRoutingStore.md)
- a [tenant routing store](./docs/TenantRoutingStore.md)
- a [selector property-based routing store](./docs/SelectorPropertyRoutingStore.md)
- a [compressing store](./docs/CompressingStore.md)
- a [deduplicating store](./docs/DeduplicatingStore.md)
- an [encrypting store](./docs/EncryptingStore.md)
- a [caching store](./docs/CachingStore.md)
- an [aggregating store](./docs/AggregatingStore.md)
- a [dummy store](./docs/DummyStore.md)

# Build

This project uses a Maven build using templates from the [Acosix Alfresco Maven](https://github.com/Acosix/alfresco-maven) project and produces module AMPs, regular Java *classes* JARs, JavaDoc and source attachment JARs, as well as installable (Simple Alfresco Module) JAR artifacts for the Alfresco Content Services and Share extensions. If the installable JAR artifacts are used for installing this module, developers / users are advised to consult the 'Dependencies' section of this README.

## Maven toolchains

By inheritance from the Acosix Alfresco Maven framework, this project uses the [Maven Toolchains plugin](http://maven.apache.org/plugins/maven-toolchains-plugin/) to allow potential cross-compilation against different Java versions. This plugin is used to avoid potentially inconsistent compiler and library versions compared to when only the source/target compiler options of the Maven compiler plugin are set, which (as an example) has caused issues with some Alfresco releases in the past where Alfresco compiled for Java 7 using the Java 8 libraries.
In order to build the project it is necessary to provide a basic toolchain configuration via the user specific Maven configuration home (usually ~/.m2/). That file (toolchains.xml) only needs to list the path to a compatible JDK for the Java version required by this project. The following is a sample file defining a Java 7 and 8 development kit.

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
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>1.7</version>
      <vendor>oracle</vendor>
    </provides>
    <configuration>
      <jdkHome>C:\Program Files\Java\jdk1.7.0_80</jdkHome>
    </configuration>
  </toolchain>
</toolchains>
```

This is used in the branch targeting Alfresco 4.2 which requires Java 7, while the branch for Alfresco 5.0 / 5.1 as well as master (Alfresco 5.2 and newer) all use Java 8.

## Docker-based integration tests

In a default build using ```mvn clean install```, this project will build the extension for Alfresco Content Services, executing regular unit-tests without running integration tests. The integration tests of this project are based on Docker and require a Docker engine to run the necessary components (PostgreSQL database as well as Alfresco Content Services). Since a Docker engine may not be available in all environments of interested community members / collaborators, the integration tests have been made optional. A full build, including integration tests, can be run by executing

```text
mvn clean install -Ddocker.tests.enabled=true
```

## Dependencies

This module depends on the following projects / libraries:

- [Tukaani XZ for Java](https://tukaani.org/xz/java.html) (Public Domain)
- Acosix Alfresco Utility (Apache License, Version 2.0) - core extension

The AMP of this project includes the Tukaani XZ for Java JAR. The Acosix Alfresco Utility project provides the core extension for Alfresco Content Services as a separate artifact from the full module, which needs to be installed in Alfresco Content Services before the AMP of this project can be installed.

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