# Standard File Store

The store type **_defaultTenantFileStore_** implemented by the class _de.acosix.alfresco.simplecontentstores.repo.store.file.TenantRoutingFileContentStore_ is for the most part identical to the default Alfresco class _org.alfresco.repo.tenant.TenantRoutingFileContentStore_. The Alfresco class _TenantRoutingFileContentStore_ is actually used as the default store in the Alfresco _unencrypted_ ContentStore subsystem variant, even in systems without multi-tenancy enabled (the majority of installations). It allows for content of the default system tenant to be stored in a configured directory, while other tenants use a directory configured at the time of tenant creation. The primary difference to the default Alfresco store is that the **_defaultTenantFileStore_** supports all the configuration properties of the **_standardFileStore_** store type and applies these to each store instance created for the various tenants.

## Relation with other stores

This type of store can only be used as a terminal store, and typically acts as the backing store in routing or transforming store setups. It should generally be avoided to have two instances of this store type point to the same file system directory, especially if both instances use distinct content URL protocols - which is the recommendation - since this can lead to the Alfresco content URL orphan handling accidentally deleting content that is still referenced by foreign content URLs. 

## Configuration Properties

This store can be selected by using the store type **_defaultTenantFileStore_**.

| name | type | description | default | optional |
| :---| :--- | :--- | :--- | :--- |
| protocol | value | the protocol to be used on content URLs | ``store`` | yes |
| rootAbsolutePath | value | the path to the directory in which to store content |  | no |
| readOnly | value | ``true``/``false`` to mark the store as ready-only | ``false`` | yes |
| allowRandomAccess | value | ``true``/``false`` to mark the store as capable of providing random access to content files | ``false`` | yes |
| deleteEmptyDirs | value | ``true``/``false`` to allow store to delete empty directories after deleting content files | ``false`` | yes |
| fixedLimit | value | the fixed file size limit for content items stored in this store | | yes |
| contentLimitProvider | ref | the limit provider for content items stored in this store | | yes |
| fileContentUrlProvider | ref | the provider for generation of content URLs of new items in this store (Alfresco 5.2+ only) | | yes |

Note that the configuration for _fixedLimit_ and _contentLimitProvider_ affect the same functionality of the store: limiting the size of files that can be stored. There is no inherent precedence between the two parameters and their initialisation order is left undefined, leaving it to the internal ordering of the Spring framework. It is therefore required to always only set one of these two properties for a reproducible behaviour.

## Configuration example

```text
simpleContentStores.enabled=true

simpleContentStores.customStores=myCustomTenantFileStore
simpleContentStores.rootStore=myCustomTenantFileStore

simpleContentStores.customStore.myCustomTenantFileStore.type=standardFileStore
simpleContentStores.customStore.myCustomTenantFileStore.value.rootAbsolutePath=/srv/alfresco/alf_data/myContentStore
simpleContentStores.customStore.myCustomTenantFileStore.value.deleteEmptyDirs=true
simpleContentStores.customStore.myCustomTenantFileStore.value.fixedLimit=104857600
```