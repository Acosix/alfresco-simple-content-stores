# Standard File Store

The store type **_standardFileStore_** implemented by the class _de.acosix.alfresco.simplecontentstores.repo.store.file.FileContentStore_ is for the most part identical to the default Alfresco class _org.alfresco.repo.content.filestore.FileContentStore_. It allows for content to be stored in a configured directory. The primary difference to the default Alfresco store is that the **_standardFileStore_** can be configured to use a specific URL protocol for its content URLs in order to help differentiate the store from others, e.g. in routing stores.

## Relation with other stores

This type of store can only be used as a terminal store, and typically acts as the backing store in routing or transforming store setups. It should generally be avoided to have two instances of this store type point to the same file system directory, especially if both instances use distinct content URL protocols - which is the recommendation - since this can lead to the Alfresco content URL orphan handling accidentally deleting content that is still referenced by foreign content URLs. 

## Configuration Properties

This store can be selected by using the store type **_standardFileStore_**.

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

simpleContentStores.customStores=myCustomFileStore
simpleContentStores.rootStore=myCustomFileStore

simpleContentStores.customStore.myCustomFileStore.type=standardFileStore
simpleContentStores.customStore.myCustomFileStore.value.rootAbsolutePath=/srv/alfresco/alf_data/myContentStore
simpleContentStores.customStore.myCustomFileStore.value.deleteEmptyDirs=true
simpleContentStores.customStore.myCustomFileStore.value.fixedLimit=104857600
```