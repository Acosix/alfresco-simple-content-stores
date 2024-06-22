# Selector Property Content Store

The store type **_selectorPropertyRoutingStore_** implemented by the class _de.acosix.alfresco.simplecontentstores.repo.store.routing.SelectorPropertyContentStore_ acts as a router of content to one of multiple backing content stores based on the value of a configurable node property. This base functionality is similar to the Alfresco Enterprise content store selector, but in contrast to the Enterprise store, the **_selectorPropertyRoutingStore_** supports an arbitrary property to be configured as the selector, and also allows the functionality to copy / move content between stores on a change of the property value to be configurable and overridable. 

## Relation with other stores

This type of store can only be used as a facade to two or more stores. Multiple instances of this store type can be set up to act on separate selector properties. It is also possible to set up multiple instances of this store type which use the same selector property, but these instances **should not** be contained in the same chain of stores to access individual content files and **should** use backing stores with unique content URL protocols to ensure correct store move / copy behaviour on property value changes.

## Copying / Moving Content between Stores

This store supports copying / moving any content from one backing store to another when the value of the configured selector property changes on a node. This functionality must be explicitly enabled via the configuration. When enabled, the store will register as a behaviour on the type or aspect which defines the configured selector property. This behaviour will trigger on any property update (excluding the initial node creation), addition or removal of the defining aspect (if property is being defined by an aspect). Unless overridden by the value of a configurable override property, the store will pick the target store for the new value of the selector property, and if that store is not the same as the old, will try to copy the existing content to that store. As part of this copy operation, the store will first check whether the content already exists in the target store, and only copy the content if it doesn't. In any case, the content URL of the content property / properties will be updated accordingly with the new value, if the URL has changed at all.

Instances of this store type will only ever copy contents between backing stores. Similar to Alfresco default behaviour for deleting content, the content in the old location will not be actively deleted by default. If the previous content URL is no longer associated with any other content data instance in Alfresco, the URL will be marked as an orphan and be be eligible for a move into the contentstore.deleted directory after a configurable grace period (default 14 days) has elapsed. By configuring the Alfresco orphan cleanup to be "eager", that is immediately process any orphaned content URLs at the end of a transaction, the copy operation of this store can effectively be turned into a move operation.

It is **vital** for the copy / move behaviour of this type of content store and the Alfresco orphan content cleanup that  the content URLs of any backing stores be distinguishable. If a content file is moved from one store to another, and the resulting content URL is identical to the existing one, the file in the old store will never be cleaned up until the content URL changes for another reason or the node is actually properly deleted. It is therefore **recommended** that backing stores at least use unique content URL protocols to differentiate their content URLs.

## Configuration Properties

This store can be selected by using the store type **_selectorPropertyRoutingStore_**.

| name | type | description | default | optional |
| :---| :--- | :--- | :--- | :--- |
| selectorClassName | value | prefixed or full QName of type / aspect associated with the selector property (relevant for handling changes via policies) |  | no |
| selectorPropertyName | value | prefixed or full QName of the selector property |  | no |
| selectorValuesConstraintShortName | value | short name of a list-of-values constraint that should dynamically be registered using configured selector values as the "allowedValues" list (the content model for the selector property may reference this via a REGISTERED constraint) |  | yes |
| storeBySelectorPropertyValue | map(ref) | backing content stores keyed by the property values that select them |  | no |
| fallbackStore | ref | default backing store to use when either no value exists for the property selector or the value is not mapped by storeBySelectorPropertyValue |  | no |
| routeContentPropertyNames | list(value) | list of content property QNames (prefixed or full) for which the store should route content - if set only content for the specified properties will be routed based on the selector property, all other content will be directed to the fallbackStore |  | yes |
| moveStoresOnChange | value | ``true``/``false`` to mark if content should be moved between backing stores when the selector property value changes | ``false`` | yes |
| moveStoresOnChangeOptionPropertyName | value | prefixed or full QName of a single-valued ``d:boolean`` property on nodes that can override moveStoresOnChange - if set, the boolean value of this property overrides the default _moveStoresOnChange_ setting if not null |  | yes |

## Configuration example

```text
simpleContentStores.enabled=true

simpleContentStores.customStores=specialStoreFileStore,lessSpecialFileStore,propertyRoutingStore,defaultTenantFileContentStore
simpleContentStores.rootStore=propertyRoutingStore

simpleContentStores.customStore.propertyRoutingStore.type=selectorPropertyRoutingStore
simpleContentStores.customStore.propertyRoutingStore.ref.fallbackStore=defaultTenantFileContentStore
simpleContentStores.customStore.propertyRoutingStore.value.selectorClassName=cm:storeSelector
simpleContentStores.customStore.propertyRoutingStore.value.selectorPropertyName=cm:storeName
simpleContentStores.customStore.propertyRoutingStore.map.storeBySelectorPropertyValue.ref.specialStore=specialStoreFileStore
simpleContentStores.customStore.propertyRoutingStore.map.storeBySelectorPropertyValue.ref.lessSpecialStore=lessSpecialStoreFileStore
simpleContentStores.customStore.propertyRoutingStore.value.moveStoresOnChange=true

simpleContentStores.customStore.specialStoreFi.type=standardFileStore
simpleContentStores.customStore.specialStoreFi.value.rootAbsolutePath=/mnt/alfresco/alf_data/special-contentstore

simpleContentStores.customStore.lessSpecialStoreFileStore.type=standardFileStore
simpleContentStores.customStore.lessSpecialStoreFileStore.value.rootAbsolutePath=/mnt/alfresco/alf_data/less-special-contentstore
```