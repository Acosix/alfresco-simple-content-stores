# Site Routing Content Store

The store type **_typeRoutingStore_** implemented by the class _de.acosix.alfresco.simplecontentstores.repo.store.routing.TypeRoutingContentStore_ acts as a router of content to one of multiple backing content stores based on the qualified type of the associated node.

## Relation with other stores

This type of store can only be used as a facade to two or more stores.

## Copying / Moving Content between Stores

This store supports copying / moving any content from one backing store to another when the type of a node changes. This functionality must be explicitly enabled via the configuration. When enabled, the store will register as a behaviour on the node type ``sys:base``. Unless overridden by the value of a configurable override property, the store will pick the target store for the new node type, and if that store is not the same as the old, will try to copy the existing content to that store. As part of this copy operation, the store will first check whether the content already exists in the target store, and only copy the content if it doesn't. In any case, the content URL of the content property / properties will be updated accordingly with the new value, if the URL has changed at all.

This store will use the entire ancestor chain of a node's type to resolve an appropriate target store. If the specific node type has not been configured to use a particular backing store, the store will use Alfresco's DictionaryService to resolve the parent type, and check again. This procedure will be repeated along the type ancestor hierarchy until a configured store has been selected or the root type of the hierarchy been processed without a match. Only then will the store fall back to the default backing store.

Instances of this store type will only ever copy contents between backing stores. Similar to Alfresco default behaviour for deleting content, the content in the old location will not be actively deleted by default. If the previous content URL is no longer associated with any other content data instance in Alfresco, the URL will be marked as an orphan and be eligible for a move into the contentstore.deleted directory after a configurable grace period (default 14 days) has elapsed. By configuring the Alfresco orphan cleanup to be "eager", that is immediately process any orphaned content URLs at the end of a transaction, the copy operation of this store can effectively be turned into a move operation.

It is **vital** for the copy / move behaviour of this type of content store and the Alfresco orphan content cleanup that  the content URLs of any backing stores be distinguishable. If a content file is moved from one store to another, and the resulting content URL is identical to the existing one, the file in the old store will never be cleaned up until the content URL changes for another reason or the node is actually properly deleted. It is therefore **recommended** that backing stores at least use unique content URL protocols to differentiate their content URLs.

## Configuration Properties

This store can be selected by using the store type **_typeRoutingStore_**.

| name | type | description | default | optional |
| :---| :--- | :--- | :--- | :--- |
| storeByTypeName | map(ref) | backing content stores keyed by the node type name (prefixed or full QName) that select them |  | no |
| fallbackStore | ref | default backing store to use when the node is either not contained in a site or neither the sit e name nor preset are mapped by _storeBySite_ / _storeBySitePreset_ |  | no |
| routeContentPropertyNames | list(value) | list of content property QNames (prefixed or full) for which the store should route content; if set only content for the specified properties will be routed based on the selector property, all other content will be directed to the fallbackStore |  | yes |
| moveStoresOnChange | value | ``true``/``false`` if contents should be moved to a (potentially) different store when the type of a node is changed | ``false`` | yes |
| moveStoresOnChangeOverridePropertyName | value | value | prefixed or full QName of a single-valued ``d:boolean`` property on nodes that can override _moveStoresOnChange_ - if set, the boolean value of this property overrides the default _moveStoresOnChange_ setting if not null |  | yes |

## Configuration example

```text
simpleContentStores.enabled=true

simpleContentStores.customStores=archiveFileStore,typeRoutingStore,defaultTenantFileContentStore
simpleContentStores.rootStore=typeRoutingStore

simpleContentStores.customStore.typeRoutingStore.type=typeRoutingStore
simpleContentStores.customStore.typeRoutingStore.ref.fallbackStore=defaultTenantFileContentStore
simpleContentStores.customStore.typeRoutingStore.map.storeByTypeName.ref.acme\:archiveDocument=archiveFileStore

simpleContentStores.customStore.archiveFileStore.type=standardFileStore
simpleContentStores.customStore.archiveFileStore.value.rootAbsolutePath=/mnt/alfresco/alf_data/archive-contentstore
```