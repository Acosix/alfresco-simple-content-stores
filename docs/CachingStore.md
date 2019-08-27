# Caching Content Store

The store type **_standardCachingStore_** implemented by the class _org.alfresco.repo.content.filestore.FileContentStore_ is one of the stores provided by default with Alfresco. It allows for content from a backing store with a high latency to be cached in a more local store with significantly lower latency. This is meant to improve access performance for frequently accessed content.

## Relation with other stores

This type of store can only be used as a facade to a backing and a cache store. Though the caching store is supposed to be a local store with lower latency, there is no requirement on it being a simple [file content store](./StandardFielStore.md), and in fact it can be any kind of store.

## Cached Content Cleaner

As long as an instance of this type of content store has been configured to use the standard quota for the cache content store, a scheduled background process (based on Quartz) will be automatically set up to check the state of the cache and clear out old entries when the quota has been exceeded. The job will be set up dynamically by the _simple-content-stores_ addon and registered with the default Quartz scheduler, so it is accessible in the _Scheduled Jobs_ tool of either the OOTBee Support Tools addon or Alfresco Enterprise Administration Console. The name of the job and trigger will use the name of the content store instance as the common prefix.

## Configuration Properties

This store can be selected by using the store type **_standardCachingStore_**.

| name | type | description | default | optional |
| :---| :--- | :--- | :--- | :--- |
| cacheName | value | name of the Alfresco cache to hold information about local cache structures |  | no |
| cacheRoot | value | the path to the directory storing locally cached content files |  | no
| backingStore | ref | the (remote) store which actually contains the content |  | no |
| cacheOnInbound | value | ``true``/``false`` to mark if new content should be written to both the backing store and the local cache | ``false`` | yes |
| maxCacheTries | value | the limit for attempts to locally cache a content file during read | ``2`` | yes |
| quotaStrategy | ref | the cache quota strategy implementation - if this is set, any other quota-related properties will be ignored | | yes |
| useStandardQuotaStrategy | value | ``true``/``false`` to mark if the standard quota strategy implementation should be used - if not set to ``true`` and if no _quotaStrategy_ has been explicitly set, this effectively activates an "unlimited quota" strategy | ``false`` | yes |
| standardQuotaPanicThresholdPercent | value | percent of allowed max cache usage to consider the "panic" threshold and trigger immediate, asynchronous cache cleaning before writing new cache content | ``90`` | yes |
| standardQuotaCleanThresholdPercent | value | percent of allowed max cache usage to consider for triggering asynchronous cache cleaning after writing new cache content | ``80`` | yes |
| standardQuotaPanicThresholdPercent | value | percent of allowed max cache usage that is considered the target result of a cache cleaning process | ``70`` | yes |
| standardQuotaTargetUsagePercent | value | percent of allowed max cache usage to consider the "panic" threshold and trigger immediate, asynchronous cache cleaning before writing new cache content - not supported in Alfresco 4.2 | ``90`` | yes |
| standardQuotaMaxUsageBytes | value | the allowed max cache usage in bytes - if this is exceeded, an aggressive cache cleaning is triggered | ``0`` | yes |
| standardQuotaMaxFileSizeMebiBytes | value | the max allowed size of an individual size in the cache in mebibytes - if this is exceeded, a content file will not be cached | ``0`` | yes |
| standardQuotaNormalCleanThresholdSeconds | value | the amount of time that should pass between two normal cache cleaning processes in seconds - aggressive cache cleaning processes will ignore this | ``0`` | yes |
| cleanerMinFileAgeMillis | value | the minimal file age in milliseconds before a cached content is considered for cleanup | ``0`` | yes |
| cleanerMaxDeleteWatchCount | value | the max amount of times a cached file will be considered/marked for deletion before it is actually deleted | ``1`` | yes |
| cleanerCronExpression | value | the CRON expression for the cleaner job for this store - if this is set it will be used to schedule the job and repeat settings will be ignored |  | yes |
| cleanerStartDelay | value | the amount of milliseconds to delay the start of the trigger relative to its initialization | ``0`` | yes |
| cleanerRepeatInterval | value | the interval between cleaner job runs in milliseconds | ``30000 ``| yes |
| cleanerRepeatCount | value | the amount of times the cleaner job should run repeatedly | ``-1`` ("indefinitely") | yes |

## Configuration example

```text
simpleContentStores.enabled=true

simpleContentStores.customStores=myCachingFileStore,mySlowFileStore
simpleContentStores.rootStore=myCachingFileStore

simpleContentStores.customStore.myCachingFileStore.type=standardCachingStore
simpleContentStores.customStore.myCachingFileStore.ref.backingStore=mySlowFileStore
simpleContentStores.customStore.myCachingFileStore.value.cacheRoot=/mnt/ssd/alfresco/alf_data/cacheStore
simpleContentStores.customStore.myCachingFileStore.value.cacheInbound=true
simpleContentStores.customStore.myCachingFileStore.value.useStandardQuotaStrategy=true
simpleContentStores.customStore.myCachingFileStore.value.standardQuotaMaxUsageBytes=1073741824

simpleContentStores.customStore.mySlowFileStore.type=defaultTenantFileContentStore
simpleContentStores.customStore.mySlowFileStore.value.rootAbsolutePath=/mnt/slowHdd/alfresco/alf_data/contentstore
simpleContentStores.customStore.mySlowFileStore.value.deleteEmptyDirs=true
```