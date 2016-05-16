# Simple Content Stores
This addon provides a set of simple / common content store imnplementations to enhance any installation of Alfresco Community or Enterprise. It also provides a configuration mechanism that supports configuring custom content stores without any need for Spring bean definition / XML manipulation or overriding.

### Content Store types
The addon currently provides the following content store types:

- "Selector Property" content store which routes content to different backing content stores based on the value of a specific single-valued text property (similar to Enterprise store selector aspect store but configurable for any property)

The following store types are planned at this time:
- deduplicating store based on content digests (based on previous work in [Alfresco Sumit 2013 hackathon](https://github.com/AFaust/content-stores))

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

The different types of stores define their individual set of required / optional configuration properties.

Stores of type "selectorPropertyStore" support the following properties:
- selectorClassName - the prefixed or full QName of the type / aspect that is associated with the selector property (relevant for handling changes via policies)
- selectorPropertyName - the prefixed or full QName of the selector property
- selectorValuesConstraintShortName - optional short name of a list-of-values constraint that should dynamically be registered using configured selector values as the "allowedValues" list (the content model for the selector property can reference this via a REGISTERED constraint)
- storeBySelectorPropertyValue - a map of backing content stores keyed by the property values that should select them
- fallbackStore - the default backing store to use when either no value exists for the property selector or the value is not mapped by storeBySelectorPropertyValue
- routeContentPropertyNames - an optional list of content property QNames (prefixed or full) for which the store should route content; if set only content for the specified properties will be routed based on the selector property, all other content will be directed to the fallbackStore
- moveStoresOnChange - true/false to mark if content should be moved between backing stores when the selector property value changes (false by default)
- moveStoresOnChangeOptionPropertyName - the optional, prefixed or full QName of a single-valued d:boolean property on nodes that can override moveStoresOnChange

Stores of type "standardFileStore" support the following properties:
- rootDirectory - the path to the directory in which to store content
- readOnly - true/false to mark the store as ready-only (false by default)
- allowRandomAccess - true/false to mark the store as capable of providing random access to content files (false by default)
- deleteEmptyDirs - true/false to allow store to delete empty directories (false by default)