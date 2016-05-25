# Simple Content Stores
This addon provides a set of simple / common content store imnplementations to enhance any installation of Alfresco Community or Enterprise. It also provides a configuration mechanism that supports configuring custom content stores without any need for Spring bean definition / XML manipulation or overriding.

### Content Store types
The addon currently provides the following content store types:

- "Selector Property" content store which routes content to different backing content stores based on the value of a specific single-valued text property (similar to Enterprise store selector aspect store but configurable for any property)
- deduplicating content store which uses hash / message digest mechanism to construct content URLs and ensure that stored content is unique (no two files in storage a binary identical)

The following store types are planned at this time:
- compressing store which transparently (un)compresses content to/from storage
- content stores to store / retrieve content from remote locations (not file-based, e.g. S3 or arbitrary HTTP)
- container stores which (asynchronously) combines content files into an aggregate (to reduce file handles / optimize compression)

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

The following types can currently be used to define custom content stores:

- selectorPropertyStore (the "Selector Property" store)
- standardFileStore (Alfresco standard file content store, potentially storing content in a custom directory)
- deduplicatingFacadeStore (a deduplicating store that acts as a facade to an actual, physical store)
- standardCachingStore (Alfresco standard caching content store, retrieving and temporarily storing content from a remote, potentially slow content store)

The different types of stores define their individual set of required / optional configuration properties.

| store type | prop. name | prop. type | description | default | optional |
| :--- | :---| :--- | :--- | :--- | :--- |
| selectorPropertyStore | selectorClassName | value | prefixed or full QName of type / aspect associated with the selector property (relevant for handling changes via policies) |  | no |
| selectorPropertyStore | selectorPropertyName | value | prefixed or full QName of the selector property |  | no |
| selectorPropertyStore | selectorValuesConstraintShortName | value | short name of a list-of-values constraint that should dynamically be registered using configured selector values as the "allowedValues" list (the content model for the selector property can reference this via a REGISTERED constraint) |  | yes |
| selectorPropertyStore | storeBySelectorPropertyValue | map(ref) | backing content stores keyed by the property values that select them |  | no |
| selectorPropertyStore | fallbackStore | ref | default backing store to use when either no value exists for the property selector or the value is not mapped by storeBySelectorPropertyValue |  | no |
| selectorPropertyStore | routeContentPropertyNames | value | list of content property QNames (prefixed or full) for which the store should route content; if set only content for the specified properties will be routed based on the selector property, all other content will be directed to the fallbackStore |  | yes |
| selectorPropertyStore | moveStoresOnChange | value | true/false to mark if content should be moved between backing stores when the selector property value changes | false | yes |
| selectorPropertyStore | moveStoresOnChangeOptionPropertyName | value | prefixed or full QName of a single-valued d:boolean property on nodes that can override moveStoresOnChange |  | yes |

Stores of type "standardFileStore" support the following properties:
- rootDirectory - the path to the directory in which to store content
- readOnly - true/false to mark the store as ready-only (false by default)
- allowRandomAccess - true/false to mark the store as capable of providing random access to content files (false by default)
- deleteEmptyDirs - true/false to allow store to delete empty directories (false by default)

Stores of type "deduplicatingFacadeStore" support the following properties:
- storeProtocol - the protocol to be used on content URLs ("store" by default - configuration currently has no effect pending workaround for "custom protocol"-limitations in default stores, e.g. Alfresco default file store)
- backingStore - reference to the (physical) store that stores the deduplicated content
- handleContentPropertyNames - an optional list of content property QNames (prefixed or full) for which the store should deduplicate content; if set only content for the specified properties will be deduplicated, all other content will be passed through to to the backingStore
- digestAlgorithm - the hash / message digest algorithm to be used for calculating content hash ("SHA-512" by default)
- digestAlgorithmProvider - the optional provider for a specific message digest algorithm (needs only be set if not using built-in Java message digest algorithms)
- pathSegments - how many path segments (in the content URL) should be used to structure content (3 by default)
- bytesPerPathSegment - how many bytes of the hash / message digest of a content should be used per path segment (2 by default)

Stores of type "standardCachingStore" support the following properties:
- cacheName - the name of the in-memory (Hazelcast) cache to hold information about local cache structures
- cacheRoot - the path to the directory storing locally cached content files
- backingStore - reference to the (remote) store which actually contains the content
- cacheOnInbound - true/false to mark if new content shoulud be written to both the backing store and the local cache (false by default)
- maxCacheTries - the limit for attempts to locally cache a content file during read (2 by default)
- quotaStrategy - reference to the cache quota strategy implementation - if this is set, any other quota-related properties wil be ignored
- useStandardQuotaStrategy - true/false to mark if the standard quota strategy implementation should be used (false by default)
- standardQuotaPanicThresholdPercent - percent of allowed max cache usage to consider the "panic" threshold and trigger immediate, asynchronous cache cleaning before writing new cache content (90 by default)
- standardQuotaCleanThresholdPercent - percent of allowed max cache usage to consider for triggering asynchronous cache cleaning after writing new cache content (80 by default)
- standardQuotaTargetUsagePercent - percent of allowed max cache usage that is considered the target result of a cache cleaning process (70 by default)
- standardQuotaMaxUsageBytes - the allowed max cache usage in bytes - if this is exceeded, an aggressive cache cleaning is triggered (0 by default)
- standardQuotaMaxFileSizeMebiBytes - the max allowed size of an individual size in the cache in mebibytes - if this is exceeded, a content file will not be cached (0 by default)
- standardQuotaNormalCleanThresholdSeconds - the amount of time that should pass between two normal cache cleaning processes in seconds - aggresive cache cleaning processes will ignore this (0 by default)
- cleanerMinFileAgeMillis - the minimal file age in milliseconds before a cached content is considered for cleanup (0 by default)
- cleanerMaxDeleteWatchCount - the max amount of times a cached file will be considered/marked for deletion before it is actually deleted (1 by default)
- cleanerCronExpression - the CRON expression for the cleaner job for this store - if this is set it will be used to schedule the job and repeat settings will be ignored
- cleanerStartDelay - the amount of milliseconds to delay the start of the trigger relative to its initialization (0 by default)
- cleanerRepeatInterval - the interval between cleaner job runs in milliseconds (30000 by default)
- cleanerRepeatCount - the amount of times the cleaner job should run repeatedly (-1 by default, meaning "indefinitely")