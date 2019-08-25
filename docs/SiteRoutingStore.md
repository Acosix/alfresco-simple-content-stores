# Site Routing Content Store

The store type **_siteRoutingStore_** implemented by the class _de.acosix.alfresco.simplecontentstores.repo.store.routing.SiteRoutingContentStore_ acts as a router of content to one of multiple backing content stores based on the location of the associated node, more specifically the site in which the node is contained. 

## Relation with other stores

This type of store can only be used as a facade to two or more stores. Multiple instances of this store type can be set up to act on separate conditions, e.g. different names of sites or site presets. Multiple instances can also be set up using the same site names or presets for distinguishing the routing, but in these constellations care must be taken to either avoid conflicting overlaps in configuration of routing conditions or enable content copying / moving between backing stores only on one layer of the routing store chain. If incorrectly configured, a setup with multiple instances of this store type in a chain may create redundant copies of the underlying content in multiple stores until finally settling on the actual target store.

## Copying / Moving Content between Stores

This store supports copying / moving any content from one backing store to another when the location of a node changes, specifically when it is moved or copied to a different site. This functionality must be explicitly enabled via the configuration. When enabled, the store will register as a behaviour on the node type ``sys:base``. This behaviour will trigger on both a move of a node or the completion of a copy operation. Unless overridden by the value of a configurable override property, the store will pick the target store for the new location, and if that store is not the same as the old, will try to copy the existing content to that store. As part of this copy operation, the store will first check whether the content already exists in the target store, and only copy the content if it doesn't. In any case, the content URL of the content property / properties will be updated accordingly with the new value, if the URL has changed at all.

Instances of this store type will only ever copy contents between backing stores. Similar to Alfresco default behaviour for deleting content, the content in the old location will not be actively deleted by default. If the previous content URL is no longer associated with any other content data instance in Alfresco, the URL will be marked as an orphan and be be eligible for a move into the contentstore.deleted directory after a configurable grace period (default 14 days) has elapsed. By configuring the Alfresco orphan cleanup to be "eager", that is immediately process any orphaned content URLs at the end of a transaction, the copy operation of this store can effectively be turned into a move operation.

It is **vital** for the copy / move behaviour of this type of content store and the Alfresco orphan content cleanup that  the content URLs of any backing stores be distinguishable. If a content file is moved from one store to another, and the resulting content URL is identical to the existing one, the file in the old store will never be cleaned up until the content URL changes for another reason or the node is actually properly deleted. It is therefore **recommended** that backing stores at least use unique content URL protocols to differentiate their content URLs.

## Configuration Properties

This store can be selected by using the store type **_siteRoutingStore_**.

| name | type | description | default | optional |
| :---| :--- | :--- | :--- | :--- |
| storeBySite | map(ref) | backing content stores keyed by the site short name that select them - either _storeBySite_ or _storeBySitePreset_ must be provided, and if both are set, any match in _storeBySite_ overrides a more generic match in _storeBySitePreset_ |  | yes |
| storeBySitePreset | map(ref) | backing content stores keyed by the site preset that select them - either _storeBySite_ or _storeBySitePreset_ must be provided, and if both are set, any match in _storeBySite_ overrides a more generic match in _storeBySitePreset_ |  | yes |
| fallbackStore | ref | default backing store to use when the node is either not contained in a site or neither the sit e name nor preset are mapped by _storeBySite_ / _storeBySitePreset_ |  | no |
| routeContentPropertyNames | list(value) | list of content property QNames (prefixed or full) for which the store should route content; if set only content for the specified properties will be routed based on the selector property, all other content will be directed to the fallbackStore |  | yes |
| moveStoresOnNodeMoveOrCopy | value | ``true``/``false`` if contents should be moved to a (potentially) different directory when a content node is moved/copied between or in/out of sites | ``false`` | yes |
| moveStoresOnNodeMoveOrCopyOverridePropertyName | value | value | prefixed or full QName of a single-valued ``d:boolean`` property on nodes that can override moveStoresOnChange - if set, the boolean value of this property overrides the default _moveStoresOnChange_ setting if not null |  | yes |

## Configuration example

```text
simpleContentStores.enabled=true

simpleContentStores.customStores=collaborationSiteFileStore,managementSiteFileStore,siteRoutingStore,defaultTenantFileContentStore
simpleContentStores.rootStore=siteRoutingStore

simpleContentStores.customStore.siteRoutingStore.type=siteRoutingStore
simpleContentStores.customStore.siteRoutingStore.ref.fallbackStore=defaultTenantFileContentStore
# default for all site with the default preset "site-dashboard" 
simpleContentStores.customStore.siteRoutingStore.map.storeBySitePreset.ref.site-dashboard=collaborationSiteFileStore
# specific site which has a dedicated store
simpleContentStores.customStore.siteRoutingStore.map.storeBySite.ref.management=managementSiteFileStore

simpleContentStores.customStore.collaborationSiteFileStore.type=standardFileStore
simpleContentStores.customStore.collaborationSiteFileStore.value.rootAbsolutePath=/mnt/alfresco/alf_data/collaboration-sites-contentstore

simpleContentStores.customStore.managementSiteFileStore.type=standardFileStore
simpleContentStores.customStore.managementSiteFileStore.value.rootAbsolutePath=/mnt/alfresco/alf_data/management-site-contentstore
```