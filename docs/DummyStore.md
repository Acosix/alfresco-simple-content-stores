# Dummy Content Store

The store type **_dummyFallbackStore_** implemented by the class _de.acosix.alfresco.simplecontentstores.repo.store.file.DummyFallbackContentStore_ is primarily intended to be used in test or development environments that need to use a replica of the production database e.g. to reproduce an issue or test content model migrations. Typically the actual content of documents is not necessarily relevant for these tests and in some cases it may be prohibited to copy the entire content store or have access to it due to the existence of sensitive content therein. In such cases it would be preferable to be able to "fake" content to be able to test with metadata only but still avoid issues due to missing content. The dummy store "fakes" content in the following ways:

* provide a mimetype-specific dummy file if provided in specific lookup paths (<tomcat>/webapps/alfresco/classes/alfresco/module/\*/dummyFallbackContentStore/dummy.<file-extension> or <tomcat>/shared/classes/alfresco/extension/dummyFallbackContentStore/dummy.<file-extension>)
* provide a mimetype-specific test file included in Alfresco for performing transformation tests (e.g. via [OOTBee Support Tools addon](https://github.com/OrderOfTheBee/ootbee-support-tools/wiki/Test%20Transform#test-transform))
* provide a file via transformation of a dummy file provided in specific lookup paths (e.g. create a PDF from text file)

**Note**: Some content items in the Alfresco Repository are expected to be of a specific syntax / structure to function properly. If the content for scripts, models or workflows deployed in the Data Dictionary is provided via this store, the startup of the Alfresco Repository, configured rules or other actions may fail. For Enterprise Edition, the license descriptor will be stored as content and cannot be provided as a dummy by this store - if it is not present, the system will be put in read-only mode.
The following content items must always be copied from a production system to a test environment, even if the dummy content store is used:
* scripts in data dictionary
* models in data dictionary
* process definitions in data dictionary
* Repository license / version descriptor (attached to nodes in the system://system store)
* dashboard / component configurations (stored below /app:company_home/st:sites/cm:surf-config/... and /app:company_home/st:sites/cm:<siteShortName>/cm:surf-config/...)
* user preferences
* tag scope caches

The last three elements are only relevant if you want to use the Share user interface or services providing backend operations specifically for Share.

## Relation with other stores

This store should never be used just on its own. It **always requires** either a routing or aggregating store in front of it. The dummy store should only ever be used as a fallback / secondary store to provide content if content could not be found in another store. If this store is used on its own, the Alfresco repository will not start/function correctly as various runtime-configuration and state files will not be available.

## Configuration Properties

This store can be selected by using the store type **_dummyFallbackStore_**.

| name | type | description | default | optional |
| :---| :--- | :--- | :--- | :--- |
| transformationCandidateSourceMimetypes| list(value) | the list of mimetypes to be considered for creating a dummy file via transformation | ``text/plain``, ``image/png``, ``application/pdf``, ... (MS Word, Excel, PowerPoint, ODT, OPS, ODP) | yes |


## Configuration example

```text
simpleContentStores.enabled=true

simpleContentStores.customStores=dummyStore,myAggregatingStore,defaultTenantFileContentStore
simpleContentStores.rootStore=myAggregatingStore

simpleContentStores.customStore.myAggregatingStore.type=aggregatingStore
simpleContentStores.customStore.myAggregatingStore.ref.primaryStore=defaultTenantFileContentStore
simpleContentStores.customStore.myAggregatingStore.list.ref.secondaryStores=dummyStore

simpleContentStores.customStore.dummyStore.type=dummyFallbackStore
```