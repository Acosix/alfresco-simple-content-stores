[![Build Status](https://travis-ci.org/AFaust/simple-content-stores.svg?branch=master)](https://travis-ci.org/AFaust/simple-content-stores)

# Simple Content Stores

This addon provides a set of simple / common content store implementations to enhance any installation of Alfresco Community or Enterprise. It also provides a mechanism that supports configuring custom content stores without any need for Spring bean definition / XML manipulation or overriding, just by using properties inside of the alfresco-global.properties file.

## Provided Stores

The [wiki](https://github.com/AFaust/simple-content-stores/wiki) contains detailed information about all the stores this addon provides, as well as their configuration properties and configuration examples. Currently, this addon provides:

- a simple [file store](https://github.com/AFaust/simple-content-stores/wiki/File-Store)
- a [site aware file store](https://github.com/AFaust/simple-content-stores/wiki/Site-File-Store)
- a [tenant aware file store](https://github.com/AFaust/simple-content-stores/wiki/Tenant-File-Store)
- a [site routing store](https://github.com/AFaust/simple-content-stores/wiki/Site-Routing-Store)
- a [tenant routing store](https://github.com/AFaust/simple-content-stores/wiki/Tenant-Routing-Store)
- a [selector property-based routing store](https://github.com/AFaust/simple-content-stores/wiki/Selector-Property-Store)
- a [compressing store](https://github.com/AFaust/simple-content-stores/wiki/Compressing-Store)
- a [deduplicating store](https://github.com/AFaust/simple-content-stores/wiki/Deduplicating-Store)
- an [encrypting store](https://github.com/AFaust/simple-content-stores/wiki/Encrypting-Store)
- a [caching store](https://github.com/AFaust/simple-content-stores/wiki/Caching-Store)
- an [aggregating store](https://github.com/AFaust/simple-content-stores/wiki/Aggregating-Store)

## To be moved into the [wiki](https://github.com/AFaust/simple-content-stores/wiki)

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