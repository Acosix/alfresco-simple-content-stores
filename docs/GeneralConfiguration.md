# General Configuration

## Enablement

In Alfresco 5.0 and above, this addon adds a ContentStore subsystem variant _simple-content-stores_ as an alternative to the default _unencrypted_ and Enterprise-only _cryptodoc_ variants. In Alfresco 4.2, this addon modifies the Alfresco Spring context, creating and configuring custom content store instances. In both cases, it is not active by default and needs to be explicitely activated by setting:

```text
simpleContentStores.enabled=true
```

via the _alfresco-global.properties_ configuration file.

The default configuration of the new subsystem variant will create and configure a single file-based content store nearly identical to the default file-based content store.

## Custom Stores

This addon can be configured to create and configure additional content stores by using custom properties in either _alfresco-global.properties_, or a custom _\*.properties_ file inside the path _alfresco/extension/subsystems/ContentStore/simple-content-stores/simple-content-stores/_. The use of _alfresco-global.properties_ may be the most familiar to any Alfresco administrator as it is most often documented, but the alternative is considered the intended correct approach (any keys in _\*.properties_ files here have - by design of Alfresco subsystems - the final override over _alfresco-global.properites_ as far as the subsystem is concerned). Only for the Alfresco 4.2 specific branch of this addon is the _alfresco-global.properties_ file the only option, since that version does not use the subsystem approach for handling content stores.

Content store configuration can be split into the following primary aspects:

1. configuring a unique list (comma-separated) of content store IDs, used to refer to individual content store instances in all further configuration
2. configuring each content store instance
3. defining the root content store (entry point for the ContentService)

### Defining Custom Stores

The unique list of content store IDs is configured via

```text
simpleContentStores.customStores=mySiteFileStore,mySiteRoutingStore,defaultTenantFileContentStore
```

Each store ID in the list must refer to an individual store that is defined with its own set of configuration properties, depending on the type of store. Only the ```defaultTenantFileContentStore``` does not need to be defined, as this is already defined by the _simple-content-stores_ addon out-of-the-box to represent a default store nearly identical to the default file-based store of Alfresco.

### Configuring Custom Stores

An individual content store instance is configured by first specifying its type, and then setting any configuration properties or references as defined / required by that type. An example for the list of content store IDs in the section "Defining Custom Stores" could be

```text
simpleContentStores.customStore.mySiteRoutingStore.type=siteRoutingStore
# store all content for site-dashboard sites in 'mySiteFileStore'
simpleContentStores.customStore.mySiteRoutingStore.map.storeBySitePreset.ref.site-dashboard=mySiteFileStore
# store everything else in the default store
simpleContentStores.customStore.mySiteRoutingStore.ref.fallbackStore=defaultTenantFileContentStore
simpleContentStores.customStore.mySiteRoutingStore.value.moveStoresOnNodeMoveOrCopy=true

simpleContentStores.customStore.mySiteFileStore.type=siteRoutingFileStore
simpleContentStores.customStore.mySiteFileStore.value.protocol=site-store
# custom storage path separate from default alf_data/contentstore
simpleContentStores.customStore.mySiteFileStore.value.rootAbsolutePath=${dir.root}/contentstore.site
# create a top-level sub-directory to distinguish between content of different sites
simpleContentStores.customStore.mySiteFileStore.value.useSiteFolderInGenericDirectories=true
```

The documentation pages for the individual content store types specify all the supported configuration properties for the individual types. This addon uses a set of naming conventions for determining the full name of a configuration property. The following name patterns are in use:

* simple values: _simpleContentStores.customStores.**&lt;storeId&gt;**.value.**&lt;propertyName&gt;**=**&lt;value&gt;**_
* bean references: _simpleContentStores.customStores.**&lt;storeId&gt;**.ref.**&lt;propertyName&gt;**=**&lt;springBeanId&gt;**_
* map of values: _simpleContentStores.customStores.**&lt;storeId&gt;**.map.**&lt;propertyName&gt;**.value.**&lt;mapKey&gt;**=**&lt;value&gt;**_
* map of bean references: _simpleContentStores.customStores.**&lt;storeId&gt;**.map.**&lt;propertyName&gt;**.ref.**&lt;mapKey&gt;**=**&lt;springBeanId&gt;**_

**Note**: The Spring bean ID for custom stores is the same as the content store ID defined via _simpleContentStores.customStores_.
Any Spring bean from either the _simple-content-stores_ subsystem or the global application context can be referenced with this configuration approach. This allows developers / administrators to integrate the stores created by the addon with components of their own design, e.g. quota policies / strategies or custom content stores defined externally to _simple-content-stores_.

### Configuring the Root Store

By default the Alfresco ContentService will use the default file-based store for storing any content. If custom stores have been configured, this has to be changed to use one of the custom stores as the actual entry point into the configured custom ContentStore setup. Only one content store can be defined as the root store, and any other custom content stores need to be set up as delegates of this store (via its type-specific configuration options) or they will not be used at all.

The root store can be configured by specifying

```text
simpleContentStores.rootStore=mySiteRoutingStore
```

where the value must refer to a content store ID defined in the _customStores_ list. The default value of this addon is _defaultTenantFileContentStore_.