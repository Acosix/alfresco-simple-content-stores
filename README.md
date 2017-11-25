[![Build Status](https://travis-ci.org/Acosix/alfresco-simple-content-stores.svg?branch=master)](https://travis-ci.org/Acosix/alfresco-simple-content-stores)

# About

This addon provides a set of simple / common content store implementations to enhance any installation of Alfresco Community or Enterprise. It also provides a mechanism that supports configuring custom content stores without any need for Spring bean definition / XML manipulation or overriding, just by using properties inside of the alfresco-global.properties file.

## Compatibility

This branch is built to be compatible with Alfresco 5.0/5.1. It may be used on either Community or Enterprise Edition. Separate branches exist for compatibility versions targeting Alfresco [4.2](https://github.com/Acosix/alfresco-simple-content-stores/tree/alfresco-4.2) and [5.2 and above](https://github.com/Acosix/alfresco-simple-content-stores/tree/master).

## Provided Stores

The [wiki](https://github.com/Acosix/alfresco-simple-content-stores/wiki) contains detailed information about all the stores this addon provides, as well as their configuration properties and configuration examples. Currently, this addon provides:

- a simple [file store](https://github.com/Acosix/alfresco-simple-content-stores/wiki/File-Store)
- a [site aware file store](https://github.com/Acosix/alfresco-simple-content-stores/wiki/Site-File-Store)
- a [tenant aware file store](https://github.com/Acosix/alfresco-simple-content-stores/wiki/Tenant-File-Store)
- a [site routing store](https://github.com/Acosix/alfresco-simple-content-stores/wiki/Site-Routing-Store)
- a [tenant routing store](https://github.com/Acosix/alfresco-simple-content-stores/wiki/Tenant-Routing-Store)
- a [selector property-based routing store](https://github.com/Acosix/alfresco-simple-content-stores/wiki/Selector-Property-Store)
- a [compressing store](https://github.com/Acosix/alfresco-simple-content-stores/wiki/Compressing-Store)
- a [deduplicating store](https://github.com/Acosix/alfresco-simple-content-stores/wiki/Deduplicating-Store)
- an [encrypting store](https://github.com/Acosix/alfresco-simple-content-stores/wiki/Encrypting-Store)
- a [caching store](https://github.com/Acosix/alfresco-simple-content-stores/wiki/Caching-Store)
- an [aggregating store](https://github.com/Acosix/alfresco-simple-content-stores/wiki/Aggregating-Store)

# Maven usage

This addon is being built using the [Acosix Alfresco Maven framework](https://github.com/Acosix/alfresco-maven) and produces both AMP and installable JAR artifacts. Depending on the setup of a project that wants to include the addon, different approaches can be used to include it in the build.

## Build

This project can be build simply by executing the standard Maven build lifecycles for package, install or deploy depending on the intent for further processing. A Java Development Kit (JDK) version 8 or higher is required for the build of the master branch, while the branches targeting Alfresco 4.2 and 5.0/5.1 require Java 7.

By inheritance from the Acosix Alfresco Maven framework, this project uses the [Maven Toolchains plugin](http://maven.apache.org/plugins/maven-toolchains-plugin/) to allow cross-compilation against different Java versions. This is used in the branches targeting Alfresco 4.2 and 5.0/5.1. In order to build the project it is necessary to provide a basic toolchain configuration via the user specific Maven configuration home (usually ~/.m2/). That file (toolchains.xml) only needs to list the path to a compatible JDK for the Java version required by this project. The following is a sample file defining a Java 7 and 8 development kit.

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

The simplest option to include the addon in an All-in-One project is by declaring a dependency to the installable JAR artifact. Alternatively, the AMP package may be included which typically requires additional configuration in addition to the dependency.

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
    <groupId>de.acosix.alfresco.simplecontentstores-50</groupId>
    <artifactId>de.acosix.alfresco.simplecontentstores.repo</artifactId>
    <version>1.0.0.0-SNAPSHOT</version>
    <type>jar</type>
    <classifier>installable</classifier>
</dependency>

<!-- OR -->

<!-- AMP packaging -->
<dependency>
    <groupId>de.acosix.alfresco.simplecontentstores-50</groupId>
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
                <groupId>de.acosix.alfresco.simplecontentstores-50</groupId>
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
        <groupId>de.acosix.alfresco.simplecontentstores-50</groupId>
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

Alternatively you can use the alfresco-mmt.jar to install the modules as [described in the documentation](http://docs.alfresco.com/5.1/concepts/dev-extensions-modules-management-tool.html).

## Manual "installation" using JAR files

Some addons and some other sources on the net suggest that you can install **any** addon by putting their JARs in a path like &lt;tomcat&gt;/lib, &lt;tomcat&gt;/shared or &lt;tomcat&gt;/shared/lib. This is **not** correct. Only the most trivial addons / extensions can be installed that way - "trivial" in this case means that these addons have no Java class-level dependencies on any component that Alfresco ships, e.g. addons that only consist of static resources, configuration files or web scripts using pure JavaScript / Freemarker.

The only way to manually install an addon using JARs that is **guaranteed** not to cause Java classpath issues is by dropping the JAR files directly into the &lt;tomcat&gt;/webapps/alfresco/WEB-INF/lib (Repository-tier) or &lt;tomcat&gt;/webapps/share/WEB-INF/lib (Share-tier) folders.

For this addon the following JARs need to be dropped into &lt;tomcat&gt;/webapps/alfresco/WEB-INF/lib:

 - de.acosix.alfresco.simplecontentstores.repo-&lt;version&gt;-installable.jar
 - xz-1.5.jar (3rd-party JAR for compression)
 
If Alfresco has been setup by using the official installer, another, **explicitly recommended** way to install the module manually would be by dropping the JAR(s) into the &lt;alfresco&gt;/modules/platform (Repository-tier) or &lt;alfresco&gt;/modules/share (Share-tier) folders.

# To be moved into the [wiki](https://github.com/Acosix/alfresco-simple-content-stores/wiki)

### Content Store types
The addon currently provides the following content store types:

- "Selector Property" content store which routes content to different backing content stores based on the value of a specific single-valued text property (similar to Enterprise store selector aspect store but configurable for any property)
- deduplicating content store which uses hash / message digest mechanism to construct content URLs and ensure that stored content is unique (no two files in storage a binary identical)
- "better" file content store allowing use of custom store protocols to better differentiate content via URLs and support better orphan handling in routing stores
- site-aware routing content store - content store which routes content to different backing content stores based on the name or preset of the site a content is located it
- site-aware, multi-directory file content store - an extension of the "better" file content store - allowing different directories to be used to separately store site content based on either site name or site preset
- tenant routing content store
- compressing content store supporting transparent (de)compressing
- encrypted content store supporting encryption at rest

The following store types are planned at this time:
- content stores to store / retrieve content from remote locations (not file-based, e.g. S3 or arbitrary HTTP)
- container stores which (asynchronously) combines content files into an aggregate (to reduce file handles / optimize compression)
- tenant-aware, multi-directory file content store

### Content Store configuration without messing with Spring XML / beans
Setting up a custom content store configuration in standard Alfresco requires writing Spring bean definitions in XML, understanding where to place configuration files and handling references to other content stores defined in either Alfresco or 3rd-party addon module Spring files. This can be very daunting for users / administrators new to Alfresco, and is unneccessarily complex / error-prone given how simple some other configurations in Alfresco can be.

This addon provides a 100% alfresco-global.properties based configuration mechanism for content stores that does not require writing any Spring bean definitions in XML, but can seamlessly integrate with any Spring-defined content stores.


```
# by default the addon is inactive - you can activate it by setting this property to true
simpleContentStores.enabled=false

# you can change the "root" content store Alfresco uses by referencing either a content store bean ID or a custom content store here
simpleContentStores.rootStore=fileContentStore

# you define the list of custom content stores configured with this addon in this property (as comma-separated values)
simpleContentStores.customStores=simpleSelectorStore

# you must define the base type of a custom content store
simpleContentStores.customStore.simpleSelectorStore.type=selectorPropertyStore

# you can configure configuration properties (based on type of store) in various ways
# by setting simple values for properties
simpleContentStores.customStore.simpleSelectorStore.value.selectorClassName=cm:storeSelector
simpleContentStores.customStore.simpleSelectorStore.value.selectorPropertyName=cm:storeName
simpleContentStores.customStore.simpleSelectorStore.value.selectorValuesConstraintShortName=defaultStoreSelector
# by setting references to other beans for properties
simpleContentStores.customStore.simpleSelectorStore.ref.fallbackStore=fileContentStore
# by setting map structures of simple keys to values / bean references
simpleContentStores.customStore.simpleSelectorStore.map.storeBySelectorPropertyValue.ref.default=fileContentStore
# by setting lists of values / bean references (as comma-separated values)
simpleContentStores.customStore.simpleSelectorStore.list.value.routeContentPropertyNames=cm:content

```

Note: The above is meant as an example to illustrate how configuration works - it is *not* meant as a complete, working example config. 


The following types can currently be used to define custom content stores:

- selectorPropertyRoutingStore (the "Selector Property" store)
- selectorPropertyStore (just an alias for the above for backwards compatibility)
- siteRoutingStore
- tenantRoutingStore
- standardFileStore (file content store very similar to the Alfresco standard with some improvements, potentially storing content in a custom directory and using a custom store protocol)
- siteRoutingFileStore (cross between siteRoutingStore and standardFileStore for simplified setup)
- aggregatingStore (Alfresco standard store supporting aggregation of content from multiple stores while writing only to one)
- deduplicatingFacadeStore (a deduplicating store that acts as a facade to an actual, physical store)
- standardCachingStore (Alfresco standard caching content store, retrieving and temporarily storing content from a remote, potentially slow content store)
- compressingFacadeStore (a store that transparently compresses content)
- encryptingFacadeStore (a store that encrypts content at rest)