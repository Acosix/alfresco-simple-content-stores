# Aggregating Content Store

The store type **_aggregatingStore_** implemented by the class _de.acosix.alfresco.simplecontentstores.repo.store.combination.AggregatingContentStore_ is for the most part identical to the default Alfresco class _org.alfresco.repo.content.replication.AggregatingContentStore_. It allows for content to be written to one primary content store, but read from multiple stores. This can be used e.g. in scenarios where a previous content store partition / disk has grow full and a new partition / disk is added later one to take over write load, while existing content can still be accessed from the old partition / disk.

This type of store provides the following small enhancements over the default Alfresco store implementation:

* content deletion also processes content in secondary stores (configurable)
* removal of unnecessary code (read-only locking during retrieval of content reader)
* check of content URL support also checks secondary stores, avoiding situations were client code may opt to not call getReader for an existing content only because isContentUrlSupported incorrectly yielded false

## Relation with other stores

This type of store can only be used as a facade to two or more stores.

## Configuration Properties

This store can be selected by using the store type **_aggregatingStore_**.

| name | type | description | default | optional |
| :---| :--- | :--- | :--- | :--- |
| primaryStore | ref | the (physical) store that content is both written to and read from |  | no |
| secondaryStores | list(ref) | the (physical) stores that content is read from |  | no |
| deleteContentFromSecondaryStores | boolean | ``true``/``false`` to mark if content deletion should attempt to delete from secondary stores as well as the primary store | ``true`` | yes |


## Configuration example

```text
simpleContentStores.enabled=true

simpleContentStores.customStores=nas1FileStore,nas2FileStore,myAggregatingStore
simpleContentStores.rootStore=myAggregatingStore

simpleContentStores.customStore.myAggregatingStore.type=aggregatingStore
simpleContentStores.customStore.myAggregatingStore.ref.primaryStore=fileContentStore
simpleContentStores.customStore.myAggregatingStore.list.ref.secondaryStores=nas1FileStore,nas2FileStore
simpleContentstores.customStore.myAggregatingStore.deleteContentFromSecondaryStores=false

simpleContentStores.customStore.nas1FileStore.type=standardFileStore
simpleContentStores.customStore.nas1FileStore.value.rootAbsolutePath=/mnt/nas1/alfresco/alf_data/contentstore
simpleContentStores.customStore.nas1FileStore.value.readOnly=true

simpleContentStores.customStore.nas2FileStore.type=standardFileStore
simpleContentStores.customStore.nas2FileStore.value.rootAbsolutePath=/mnt/nas2/alfresco/alf_data/contentstore
```