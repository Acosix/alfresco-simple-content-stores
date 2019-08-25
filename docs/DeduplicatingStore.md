# Deduplicating Content Store

The store type **_deduplicatingFacadeStore_** implemented by the class _de.acosix.alfresco.simplecontentstores.repo.store.facade.DeduplicatingContentStore_ acts as a facade to other stores, transparently deduplicating identical content on write operations to avoid storing duplicates in the backing store. Though it does not store content on its own, an instance of this type of store internally uses a temporary file content store mapped to the path _${java.io.tmpdir}/Alfresco_, the same directory handled by the Alfresco _org.alfresco.util.TempFileProvider_ class. This store is covered by the automatic cleanup process for temporary files, ensuring any temporary content is deleted after 60 to 119 minutes at the latest. Regardless of this temporary process, the deduplicating store actively tries to clean up any temporary content whenever it is no longer needed for its operation.

## Relation with other stores

This type of store can only be used as a facade to a single, other stores. As this store does not perform any modification of the content during read/write operations, there are no restrictions preventing any of the direct or indirect backing store(s) to be accessible via other chains of access that do not involve this store.

Since this type of store does not store content files on its own, it does not have access to all stored files when determining if a new content file already exists in the backing store(s). The deduplication logic of this store depends on generating a unique content URL for each content file based on a hash value of its content, which is passed to the backing store(s) for lookup and eventual write of a new content file. This facade therefore **can only be used** with backing store(s) which allow the content URL to be externally provided / forced for new content files, or which already perform some internal mapping between public content URLs and their own, internal addressing scheme. Additionally, any backing store **must** support the [wildcard content URL protocol](./Architecture.md#Wildcard_Content_URL_Protocol) defined / generically used by the _simple-content-stores_ addon for lookup of existing content.

## Configuration Properties

This store can be selected by using the store type **_deduplicatingFacadeStore_**.

| name | type | description | default | optional |
| :---| :--- | :--- | :--- | :--- |
| backingStore | ref | the store via which the content should be further processed and eventually stored | | no |
| handleContentPropertyNames | list(value) | list of content property QNames (prefixed or full) for which the store should deduplicate content; if set, only content for the specified properties will be deduplicated and all other content will be passed through to to the backingStore |  | yes |
| digestAlgorithm | value | name of hash / message digest algorithm to be used for calculating content hash | ``SHA-512`` | yes |
| digestAlgorithmProvider | value | name of provider for a specific message digest algorithm (if not built-in algorithm) |  | yes |
| pathSegments | value | how many path segments (in the content URL) should be used to structure content | ``3`` | yes |
| bytesPerPathSegment | value | how many bytes of the hash / message digest of a content should be used per path segment | ``2`` | yes |

Using the default configuration will result in content URLs of the form ``&lt;protocolOfBackingStore&gt;://e3b0/c442/98fc/e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855``, where the first 12 byte of the content hash / digest are used to build a path tree (which may result in corresponding directories to be created when stored via a file-based store), and the full hash / digest is used as the name of the content file itself.

## Configuration example

```text
simpleContentStores.enabled=true

simpleContentStores.customStores=myDedupStore,defaultTenantFileContentStore
simpleContentStores.rootStore=myDedupStore

simpleContentStores.customStore.myDedupStore.type=deduplicatingFacadeStore
simpleContentStores.customStore.myDedupStore.ref.backingStore=defaultTenantFileContentStore
simpleContentStores.customStore.myDedupStore.value.digestAlgorithm=MD5
```