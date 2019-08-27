# Site Routing File Content Store

The store type **_siteRoutingFileStore_** implemented by the class _de.acosix.alfresco.simplecontentstores.repo.store.file.SiteRoutingFileContentStore_ is combination of the [site routing](./SiteRoutingStore.md) and [file](./StandardFileStore.md) content stores. It is meant to be used as a simplification in setup when the only routing to be applied based on the name or preset of sites is between multiple directories in the file system. Using this store can greatly reduce the amount of configuration properties required to set up such a constellation.

## Relation with other stores

This type of store can only be used as a terminal store, and typically acts as the backing store in routing or transforming store setups. It should generally be avoided to have two instances of this store type point to the same file system directory / directories, especially if both instances use distinct content URL protocols - which is the recommendation - since this can lead to the Alfresco content URL orphan handling accidentally deleting content that is still referenced by foreign content URLs. Instances of this type of store can also be used as backing stores in conjunction with regular site routing content stores, but in these constellations care must be taken to either avoid conflicting overlaps in configuration of routing conditions or enable content copying / moving between backing stores only on one layer of the routing store chain. If incorrectly configured, a setup with multiple instances of this store type in a chain may create redundant copies of the underlying content in multiple stores until finally settling on the actual target store.

## Copying / Moving Content between Stores

This store supports copying / moving any content from one file system path to another when the location of a node changes, specifically when it is moved or copied to a different site. This functionality must be explicitly enabled via the configuration. When enabled, the store will register as a behaviour on the node type ``sys:base``. This behaviour will trigger on both a move of a node or the completion of a copy operation. Unless overridden by the value of a configurable override property, the store will pick the target file system path for the new location, and if that path is not the same as the old, will try to copy the existing content there. As part of this copy operation, the store will first check whether the content already exists in the target path, and only copy the content if it doesn't. In any case, the content URL of the content property / properties will be updated accordingly with the new value, if the URL has changed at all.

Instances of this store type will only ever copy contents between file system paths. Similar to Alfresco default behaviour for deleting content, the content in the old location will not be actively deleted by default. If the previous content URL is no longer associated with any other content data instance in Alfresco, the URL will be marked as an orphan and be be eligible for a move into the contentstore.deleted directory after a configurable grace period (default 14 days) has elapsed. By configuring the Alfresco orphan cleanup to be "eager", that is immediately process any orphaned content URLs at the end of a transaction, the copy operation of this store can effectively be turned into a move operation.

It is **vital** for the copy / move behaviour of this type of content store and the Alfresco orphan content cleanup that  the content URLs for any of the configured file system paths be distinguishable. If a content file is moved from one path to another, and the resulting content URL is identical to the existing one, the file in the old path will never be cleaned up until the content URL changes for another reason or the node is actually properly deleted. It is therefore **recommended** that each configured path be associated with a unique content URL protocol.

## Configuration Properties

This store can be selected by using the store type **_siteRoutingFileStore_**.

| name | type | description | default | optional |
| :---| :--- | :--- | :--- | :--- |
| protocol | value | the protocol to be used on content URLs | ``store`` | yes |
| rootAbsolutePath | value | the path to the directory in which to store content outside of sites or when neither _rootAbsolutePathsBySite_ nor _rootAbsolutePathsBySitePreset_ contain an entry for the site of the content |  | no |
| rootAbsolutePathsBySite | map(value) | the path to the directories in which to store content inside specific sites - either _rootAbsolutePathsBySite_ or _rootAbsolutePathsBySitePreset_ must be defined, and if both are defined, any match in _rootAbsolutePathsBySite_ supersedes a match in _rootAbsolutePathsBySitePreset_ |  | yes |
| protocolsBySite | map(value) | the protocols to be used on content URLs for content inside specific sites - must be defined for every site explicitly mapped in _rootAbsolutePathsBySite_ |  | yes |
| rootAbsolutePathsBySitePreset | map(value) | the path to the directories in which to store content inside sites of specific preset - either _rootAbsolutePathsBySite_ or _rootAbsolutePathsBySitePreset_ must be defined, and if both are defined, any match in _rootAbsolutePathsBySite_ supersedes a match in _rootAbsolutePathsBySitePreset_ |  | yes |
| protocolsBySitePreset | map(value) | the protocols to be used on content URLs for content inside sites of specific presets - must be defined for every site explicitly mapped in _rootAbsolutePathsBySitePreset_ |  | yes |
| readOnly | value | ``true``/``false`` to mark the store as ready-only | ``false`` | yes |
| allowRandomAccess | value | ``true``/``false`` to mark the store as capable of providing random access to content files | ``false`` | yes |
| deleteEmptyDirs | value | ``true``/``false`` to allow store to delete empty directories | ``false`` | yes |
| fixedLimit | value | the fixed file size limit for content items stored in this store | | yes
| contentLimitProvider | ref | the limit provider for content items stored in this store | | yes
| fixedLimitBySite | map(value) | the fixed file size limit for content items of a specific site stored in this store | | yes
| contentLimitProviderBySite | map(ref) | the limit provider for content items of a specific site stored in this store |  | yes |
| fixedLimitBySitePreset | map(value) | the fixed file size limit for content items in sites of a specific site preset stored in this store | | yes
| contentLimitProviderSitePreset | map(ref) | the limit provider for content items in sites of a specific site preset stored in this store |  | yes |
| useSiteFolderInGenericDirectories | value | true/false of the site name should be used to separate contents from different sites in either the rootAbsolutePath or any entry of rootAbsolutePathsBySitePreset | false | yes |
| moveStoresOnNodeMoveOrCopy | value | ``true``/``false`` if contents should be moved to a (potentially) different directory when a content node is moved/copied between or in/out of sites | ``false`` | yes |
| moveStoresOnNodeMoveOrCopyOverridePropertyName | value | prefixed or full QName of a single-valued d:boolean property on nodes that can override moveStoresOnNodeMoveOrCopy |  | yes |

Note that the configuration for _fixedLimit_ and _contentLimitProvider_ affect the same functionality of the store: limiting the size of files that can be stored. There is no inherent precedence between the two parameters and their initialisation order is left undefined, leaving it to the internal ordering of the Spring framework. It is therefore required to always only set one of these two properties for a reproducible behaviour.

## Configuration example

```text
simpleContentStores.enabled=true

simpleContentStores.customStores=siteFileContentStore
simpleContentStores.rootStore=siteFileContentStore

simpleContentStores.customStore.siteFileContentStore.type=siteRoutingFileStore
simpleContentStores.customStore.siteFileContentStore.value.rootAbsolutePath=${dir.contentstore}
simpleContentStores.customStore.siteFileContentStore.map.rootAbsolutePathsBySite.value.my-site=${dir.root}/contentstore.site
simpleContentStores.customStore.siteFileContentStore.map.protocolsBySite.value.my-site=my-site-store
simpleContentStores.customStore.siteFileContentStore.map.rootAbsolutePathsBySitePreset.value.site-dashboard=${dir.root}/contentstore.site-dashboard
simpleContentStores.customStore.siteFileContentStore.map.protocolsBySitePreset.value.site-dashboard=site-dashboard-store
simpleContentStores.customStore.siteFileContentStore.value.moveStoresOnNodeMoveOrCopy=true
simpleContentStores.customStore.siteFileContentStore.value.useSiteFolderInGenericDirectories=true
```