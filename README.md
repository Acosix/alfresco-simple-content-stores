[![Build Status](https://travis-ci.org/Acosix/alfresco-simple-content-stores.svg?branch=master)](https://travis-ci.org/Acosix/alfresco-simple-content-stores)

# About

This addon provides a set of simple / common content store implementations to enhance any installation of Alfresco Community or Enterprise. It also provides a mechanism that supports configuring custom content stores without any need for Spring bean definition / XML manipulation or overriding, just by using properties inside of the alfresco-global.properties file.

## Compatibility

This module is built to be compatible with Alfresco 5.2 and above. It may be used on either Community or Enterprise Edition. Separate branches exist for compatibility versions targeting Alfresco [4.2](https://github.com/Acosix/alfresco-simple-content-stores/tree/alfresco-4.2) and [5.0/5.1](https://github.com/Acosix/alfresco-simple-content-stores/tree/alfresco-5.0).

## Provided Stores

The [wiki](https://github.com/Acosix/alfresco-simple-content-stores/wiki) contains detailed information about all the stores this addon provides, as well as their configuration properties and configuration examples. Currently, this addon provides:

- a simple [file store](https://github.com/Acosix/alfresco-simple-content-stores/wiki/File-Store)
- a [tenant routing file store](https://github.com/Acosix/alfresco-simple-content-stores/wiki/Default-Tenant-Routing-File-Store) (for backwards compatibility with Alfresco default unencrypted content store)
- a [site file store](https://github.com/Acosix/alfresco-simple-content-stores/wiki/Site-File-Store)
- a [site routing store](https://github.com/Acosix/alfresco-simple-content-stores/wiki/Site-Routing-Store)
- a [tenant routing store](https://github.com/Acosix/alfresco-simple-content-stores/wiki/Tenant-Routing-Store)
- a [selector property-based routing store](https://github.com/Acosix/alfresco-simple-content-stores/wiki/Selector-Property-Store)
- a [compressing store](https://github.com/Acosix/alfresco-simple-content-stores/wiki/Compressing-Store)
- a [deduplicating store](https://github.com/Acosix/alfresco-simple-content-stores/wiki/Deduplicating-Store)
- an [encrypting store](https://github.com/Acosix/alfresco-simple-content-stores/wiki/Encrypting-Store)
- a [caching store](https://github.com/Acosix/alfresco-simple-content-stores/wiki/Caching-Store)
- an [aggregating store](https://github.com/Acosix/alfresco-simple-content-stores/wiki/Aggregating-Store)
- a [dummy store](https://github.com/Acosix/alfresco-simple-content-stores/wiki/Dummy-Store)

# Maven usage

This addon is being built using the [Acosix Alfresco Maven framework](https://github.com/Acosix/alfresco-maven) and produces both AMP and installable JAR artifacts. Depending on the setup of a project that wants to include the addon, different approaches can be used to include it in the build.

## Build

This project can be built simply by executing the standard Maven build lifecycles for package, install or deploy depending on the intent for further processing. A Java Development Kit (JDK) version 8 or higher is required for the build of the master and Alfresco 5.0/5.1 branches, while the branch targeting Alfresco 4.2 requires Java 7.

By inheritance from the Acosix Alfresco Maven framework, this project uses the [Maven Toolchains plugin](http://maven.apache.org/plugins/maven-toolchains-plugin/) to allow cross-compilation against different Java versions. This is used in the branch targeting Alfresco 4.2. In order to build the project it is necessary to provide a basic toolchain configuration via the user specific Maven configuration home (usually ~/.m2/). That file (toolchains.xml) only needs to list the path to a compatible JDK for the Java version required by this project. The following is a sample file defining a Java 7 and 8 development kit.

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

## Dependency in Alfresco SDK

The simplest option to include the addon in an All-in-One project is by declaring a dependency to the installable JAR artifact. Alternatively, the AMP package may be included which typically requires additional configuration in addition to the dependency. Since this addon depends on the [Acosix alfresco-utility addon](https://github.com/Acosix/alfresco-utility), the repository artifact for that addon is required to also be installed.

### Using SNAPSHOT builds

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

### As dependency in Alfresco Repository builds

```xml
<!-- JAR packaging -->
<dependency>
    <groupId>de.acosix.alfresco.utility</groupId>
    <artifactId>de.acosix.alfresco.utility.common</artifactId>
    <version>1.0.2.1</version>
    <type>jar</type>
</dependency>

<dependency>
    <groupId>de.acosix.alfresco.utility</groupId>
    <artifactId>de.acosix.alfresco.utility.repo</artifactId>
    <version>1.0.2.1</version>
    <type>jar</type>
    <classifier>installable</classifier>
</dependency>

<dependency>
    <groupId>de.acosix.alfresco.simplecontentstores</groupId>
    <artifactId>de.acosix.alfresco.simplecontentstores.repo</artifactId>
    <version>1.0.0.0-SNAPSHOT</version>
    <type>jar</type>
    <classifier>installable</classifier>
</dependency>

<!-- OR -->

<!-- AMP packaging -->
<dependency>
    <groupId>de.acosix.alfresco.utility</groupId>
    <artifactId>de.acosix.alfresco.utility.repo</artifactId>
    <version>1.0.2.1</version>
    <type>amp</type>
</dependency>

<dependency>
    <groupId>de.acosix.alfresco.simplecontentstores</groupId>
    <artifactId>de.acosix.alfresco.simplecontentstores.repo</artifactId>
    <version>1.0.0.0-SNAPSHOT</version>
    <type>amp</type>
</dependency>

<plugin>
    <artifactId>maven-war-plugin</artifactId>
    <configuration>
        <overlays>
            <overlay />
            <overlay>
                <groupId>${alfresco.groupId}</groupId>
                <artifactId>${alfresco.repo.artifactId}</artifactId>
                <type>war</type>
                <excludes />
            </overlay>
            <!-- other AMPs -->
            <overlay>
                <groupId>de.acosix.alfresco.utility</groupId>
                <artifactId>de.acosix.alfresco.utility.repo</artifactId>
                <type>amp</type>
            </overlay>
            <overlay>
                <groupId>de.acosix.alfresco.simplecontentstores</groupId>
                <artifactId>de.acosix.alfresco.simplecontentstores.repo</artifactId>
                <type>amp</type>
            </overlay>
        </overlays>
    </configuration>
</plugin>
```

For Alfresco SDK 3 beta users:

```xml
<platformModules>
    <moduleDependency>
        <groupId>de.acosix.alfresco.utility</groupId>
        <artifactId>de.acosix.alfresco.utility.repo</artifactId>
        <version>1.0.2.1</version>
        <type>amp</type>
    </moduleDependency>
    <moduleDependency>
        <groupId>de.acosix.alfresco.simplecontentstores</groupId>
        <artifactId>de.acosix.alfresco.simplecontentstores.repo</artifactId>
        <version>1.0.0.0-SNAPSHOT</version>
        <type>amp</type>
    </moduleDependency>
</platformModules>
```

# Other installation methods

Using Maven to build the Alfresco WAR is the **recommended** approach to install this module. As an alternative it can be installed manually.

## alfresco-mmt.jar / apply_amps

The default Alfresco installer creates folders *amps* and *amps_share* where you can place AMP files for modules which Alfresco will install when you use the apply_amps script. Place the AMP for the *de.acosix.alfresco.simplecontentstores.repo* module in the *amps* directory, and execute the script to install them. You must restart Alfresco for the installation to take effect.

Alternatively you can use the alfresco-mmt.jar to install the modules as [described in the documentation](http://docs.alfresco.com/5.2/concepts/dev-extensions-modules-management-tool.html).

## Manual "installation" using JAR files

Some addons and some other sources on the net suggest that you can install **any** addon by putting their JARs in a path like &lt;tomcat&gt;/lib, &lt;tomcat&gt;/shared or &lt;tomcat&gt;/shared/lib. This is **not** correct. Only the most trivial addons / extensions can be installed that way - "trivial" in this case means that these addons have no Java class-level dependencies on any component that Alfresco ships, e.g. addons that only consist of static resources, configuration files or web scripts using pure JavaScript / Freemarker.

The only way to manually install an addon using JARs that is **guaranteed** not to cause Java classpath issues is by dropping the JAR files directly into the &lt;tomcat&gt;/webapps/alfresco/WEB-INF/lib (Repository-tier) or &lt;tomcat&gt;/webapps/share/WEB-INF/lib (Share-tier) folders.

For this addon the following JARs need to be dropped into &lt;tomcat&gt;/webapps/alfresco/WEB-INF/lib:

 - de.acosix.alfresco.utility.common-&lt;version&gt;.jar
 - de.acosix.alfresco.utility.repo-&lt;version&gt;-installable.jar
 - de.acosix.alfresco.simplecontentstores.repo-&lt;version&gt;-installable.jar
 - xz-1.5.jar (3rd-party JAR for compression)
 
If Alfresco has been setup by using the official installer, another, **explicitly recommended** way to install the module manually would be by dropping the JAR(s) into the &lt;alfresco&gt;/modules/platform (Repository-tier) or &lt;alfresco&gt;/modules/share (Share-tier) folders.