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

The different types of stores define their individual set of required / optional configuration properties.

Stores of type "selectorPropertyStore" support the following properties:

| name | type | description | default | optional |
| :---| :--- | :--- | :--- | :--- |
| selectorClassName | value | prefixed or full QName of type / aspect associated with the selector property (relevant for handling changes via policies) |  | no |
| selectorPropertyName | value | prefixed or full QName of the selector property |  | no |
| selectorValuesConstraintShortName | value | short name of a list-of-values constraint that should dynamically be registered using configured selector values as the "allowedValues" list (the content model for the selector property can reference this via a REGISTERED constraint) |  | yes |
| storeBySelectorPropertyValue | map(ref) | backing content stores keyed by the property values that select them |  | no |
| fallbackStore | ref | default backing store to use when either no value exists for the property selector or the value is not mapped by storeBySelectorPropertyValue |  | no |
| routeContentPropertyNames | list(value) | list of content property QNames (prefixed or full) for which the store should route content; if set only content for the specified properties will be routed based on the selector property, all other content will be directed to the fallbackStore |  | yes |
| moveStoresOnChange | value | true/false to mark if content should be moved between backing stores when the selector property value changes | false | yes |
| moveStoresOnChangeOptionPropertyName | value | prefixed or full QName of a single-valued d:boolean property on nodes that can override moveStoresOnChange |  | yes |

Stores of type "siteRoutingStore" support the following properties:

| name | type | description | default | optional |
| :---| :--- | :--- | :--- | :--- |
| storeBySite | map(ref) | backing content stores keyed by the site short name that select them - either storeBySite or storeBySitePreset must be provided |  | yes |
| storeBySitePreset | map(ref) | backing content stores keyed by the site preset that select them - either storeBySite or storeBySitePreset must be provided |  | yes |
| fallbackStore | ref | default backing store to use when either no value exists for the property selector or the value is not mapped by storeBySelectorPropertyValue |  | no |
| routeContentPropertyNames | list(value) | list of content property QNames (prefixed or full) for which the store should route content; if set only content for the specified properties will be routed based on the selector property, all other content will be directed to the fallbackStore |  | yes |
| moveStoresOnNodeMoveOrCopy | value | true/false if contents should be moved to a (potentially) different directory when a content node is moved/copied between or in/out of sites | | yes |
| moveStoresOnNodeMoveOrCopyName | value | prefixed or full QName of a single-valued d:boolean property on nodes that can override moveStoresOnNodeMoveOrCopy |  | yes |

Stores of type "tenantRoutingStore" support the following properties:

| name | type | description | default | optional |
| :---| :--- | :--- | :--- | :--- |
| storeByTenant | map(ref) | backing content stores keyed by the tenant domains that select them |  | no |
| fallbackStore | ref | default backing store to use when either no value exists for the property selector or the value is not mapped by storeBySelectorPropertyValue |  | no |
| routeContentPropertyNames | list(value) | list of content property QNames (prefixed or full) for which the store should route content; if set only content for the specified properties will be routed based on the selector property, all other content will be directed to the fallbackStore |  | yes |

Stores of type "aggregatingStore" support the following properties:

| name | type | description | default | optional |
| :---| :--- | :--- | :--- | :--- |
| primaryStore | ref | the (physical) store that content is both written to and read from |  | no |
| secondaryStores | list(ref) | the (physical) stores that content is read from |  | no |

Stores of type "standardCachingStore" support the following properties:

| name | type | description | default | optional |
| :---| :--- | :--- | :--- | :--- |
| cacheName | value | name of the in-memory (Hazelcast) cache to hold information about local cache structures |  | no |
| cacheRoot | value | the path to the directory storing locally cached content files |  | no
| backingStore | ref | the (remote) store which actually contains the content |  | no |
| cacheOnInbound | value | true/false to mark if new content shoulud be written to both the backing store and the local cache | false | yes |
| maxCacheTries | value | the limit for attempts to locally cache a content file during read | 2 | yes |
| quotaStrategy | ref | the cache quota strategy implementation - if this is set, any other quota-related properties wil be ignored | | yes |
| useStandardQuotaStrategy | value | true/false to mark if the standard quota strategy implementation should be used | false | yes |
| standardQuotaPanicThresholdPercent | value | percent of allowed max cache usage to consider the "panic" threshold and trigger immediate, asynchronous cache cleaning before writing new cache content | 90 | yes |
| standardQuotaCleanThresholdPercent | value | percent of allowed max cache usage to consider for triggering asynchronous cache cleaning after writing new cache content | 80 | yes |
| standardQuotaPanicThresholdPercent | value | percent of allowed max cache usage that is considered the target result of a cache cleaning process | 70 | yes |
| standardQuotaTargetUsagePercent | value | percent of allowed max cache usage to consider the "panic" threshold and trigger immediate, asynchronous cache cleaning before writing new cache content | 90 | yes |
| standardQuotaMaxUsageBytes | value | the allowed max cache usage in bytes - if this is exceeded, an aggressive cache cleaning is triggered | 0 | yes |
| standardQuotaMaxFileSizeMebiBytes | value | the max allowed size of an individual size in the cache in mebibytes - if this is exceeded, a content file will not be cached | 0 | yes |
| standardQuotaNormalCleanThresholdSeconds | value | the amount of time that should pass between two normal cache cleaning processes in seconds - aggresive cache cleaning processes will ignore this | 0 | yes |
| cleanerMinFileAgeMillis | value | the minimal file age in milliseconds before a cached content is considered for cleanup | 0 | yes |
| cleanerMaxDeleteWatchCount | value | the max amount of times a cached file will be considered/marked for deletion before it is actually deleted | 1 | yes |
| cleanerCronExpression | value | the CRON expression for the cleaner job for this store - if this is set it will be used to schedule the job and repeat settings will be ignored |  | yes |
| cleanerStartDelay | value | the amount of milliseconds to delay the start of the trigger relative to its initialization | 0 | yes |
| cleanerRepeatInterval | value | the interval between cleaner job runs in milliseconds | 30000 | yes |
| cleanerRepeatCount | value | the amount of times the cleaner job should run repeatedly | -1 ("indefinitely") | yes |