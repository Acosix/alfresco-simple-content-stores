# Compressing Content Store

The store type **_compressingFacadeStore_** implemented by the class _de.acosix.alfresco.simplecontentstores.repo.store.facade.CompressingContentStore_ acts as a facade to other stores, transparently compressing/decompressing content during write and read operations. Though it does not store content on its own, an instance of this type of store internally uses a temporary file content store mapped to the path _${java.io.tmpdir}/Alfresco_, the same directory handled by the Alfresco _org.alfresco.util.TempFileProvider_ class. This store is covered by the automatic cleanup process for temporary files, ensuring any temporary content is deleted after 60 to 119 minutes at the latest. Regardless of this temporary process, the compressing store actively tries to clean up any temporary content whenever it is no longer needed for its operation.

## Relation with other stores

This type of store can only be used as a facade to a single, other stores. Any stores in which content processed by this facade may end up being stored should not be accessible via any path of configured content stores which does not also include this facade. Otherwise compressed content may be accidentally exposed without the appropriate, transparent decompression, leaving content inaccessible / unusable.

## Dependency on ContentService AOP

The correct operation of this content store facade is dependant on the addition of a custom service interceptor this addon applies to the public ContentService bean. This interceptor captures the parameters of any call to ``ContentService#getReader(NodeRef, QName)``. This information is used by this content store to retrieve the current ``ContentData`` for the content URL for which it is being asked to retrieve a ``ContentReader`` via the ``ContentStore#getReader(String)`` API, to be able to expose the real content size via the ``ContentAccessor#getSize()`` API. Without this information, the store can only expose the physical file size, which will be incorrect due to compression. While not problematic for most uses of a content reader, this can be problematic if the reported size is used for follow-on operations where a precise size is required, e.g. setting the HTTP Content-Length response header. It is **absolutely required** that any uses which need to have access to the correct content size use the public ContentService bean instead of the lower-cased implementation bean, otherwise the custom service interceptor will not be able to capture the parameters.

## Configuration Properties

This store can be selected by using the store type **_compressingFacadeStore_**.

| name | type | description | default | optional |
| :---| :--- | :--- | :--- | :--- |
| backingStore | ref | the store via which the content should be further processed and eventually stored | | no |
| compressionType | value | the type of compression to apply to content - supports the values ``gz``, ``deflate``, ``deflate64``, ``bzip2``, ``xz``, ``lzma``, ``lz4-block``, ``lz4-framed``, ``br`` (BROTLI), ``pack200``, ``snappy-framed``, ``snappy-raw`` | ``gz`` | yes |
| mimetypesToCompress | list(value) | the list of mimetypes that should be processed (de-/compressed) by this facade, supporting wildcard mimetypes in the form of "text/*" - if empty, all content will be compressed - if not set, all content will be processed |  | yes |

**Note**: If _mimetypesToCompress_ is set, compression when writing new content will only occur when the mimetype known internally to the content writer is covered by the configured patterns. This mimetype may either have been set explicitly on the writer or been derived by the automatic mimetype guessing logic of Alfresco. If the mimetype has not been set and cannot be determined, the generic _application/octet-stream_ mimetype for unknown binary content will be used to determine if compression should be applied.

## Configuration example

```text
simpleContentStores.enabled=true

simpleContentStores.customStores=myCompressingStore,defaultTenantFileContentStore
simpleContentStores.rootStore=myCompressingStore

simpleContentStores.customStore.myCompressingStore.type=compressingFacadeStore
simpleContentStores.customStore.myCompressingStore.ref.backingStore=defaultTenantFileContentStore
simpleContentStores.customStore.myCompressingStore.list.value.mimetypesToCompress=text/*,application/json,application/xhtml+xml,image/svg+xml,application/eps,application/x-javascript,application/atom+xml,application/rss+xml,message/rfc822
```