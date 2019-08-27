# General Aspects

## Content URLs

Content URLs in Alfresco are used to uniquely identify granular pieces of content. Being a URL, the only technical requirement for content URLs is to be resolveable to the appropriate piece of content, though in practice all default Alfresco content stores use the content URL as a representation of the path within the physical storage use to store content files. E.g. the default file content store uses the URL as a relative path to a configured root path to resolve files in a date-based directory structure.

The default file content store is hard-coded to always use the generic _store://_ content URL protocol. As soon as more than one instance of the default file content store is set up, e.g. aggregated via a routing content store, this can cause content URLs to loose their characteristic of a unique identification of content. When using the default Alfresco Enterprise store selector aspect content store, when the value of the selector property changes, the existing content is copied to the new target store. As is the Alfresco default, the old content will not be actively deleted, relying on orphan content cleanup. If both the old and new store are default file content stores, the copy will actually re-create the content file in the same relative path in the new store, and yield the exact same content URL, which now no longer uniquely identifies the content files in either stores. Since the content URL has not changed, the old content file is also not marked as orphaned and will thus not be moved to contentstore.deleted or otherwise removed from the old content store. In effect, this change of the selector property has left a redundant file in an old store where the user no longer wished to have the content stored.

In order to provide the various features of this addon, it was necessary to substantially deviate from the trivial default pattern of content URL composition / usage. Each terminal content store allows the content URL to be configured, so that each file in each store can be uniquely identified as far as necessary. Additionally, some stores may also add virtual markers inside the content URL to help distinguish between two pieces of content which may be stored in identical paths but different root locations, even when using the same content URL protocol. In order to generically handle existence checks or content reader retrieval operations where the exact content URL protocol to use may not be known in advance, e.g. when checking if a content file already exists before a copy / move between two stores, a new standardised "wildcard" content URL protocol has also been introduced by the addon.

### Configurable Content URL Protocols

Each terminal content store, which will either physically store a piece of content or off-load that storage to a remote system, should use a unique content URL protocol to identify any pieces of content managed by it. By having unique content URL protocols, routing stores can also easily find and access the content from multiple backing stores without risking false-positives, e.g. accidentally retrieving the content from the wrong store only because the relative path is the same. The terminal store types provided by this all provide the ability to configure one or more content URL protocols to be used, e.g. the [site routing file content store](./SiteRoutingFileStore.md) supports the following properties (among others):

| name | type | description | default | optional |
| :---| :--- | :--- | :--- | :--- |
| protocol | value | the protocol to be used on content URLs | ``store`` | yes |
| protocolsBySite | map(value) | the protocols to be used on content URLs for content inside specific sites - must be defined for every site explicitly mapped in _rootAbsolutePathsBySite_ |  | yes |
| protocolsBySitePreset | map(value) | the protocols to be used on content URLs for content inside sites of specific presets - must be defined for every site explicitly mapped in _rootAbsolutePathsBySitePreset_ |  | yes |

Note that the default value for all generic content URL protocols will still be ``store`` to be consistent with the Alfresco default, but it is **highly recommended** to configure custom protocols.

### Wildcard Content URL Protocol

All types of stores provided by this addon use a standardised, generic content URL protocol to identify "wildcard content URLs", i.e. content URLs that should be considered to match against content URLs of each store if the semantically relevant parts of the relative URL path is equal, regardless of the actual content URL protocol in use. This is used extensively in handling content copies / moves between content stores in routing content stores, e.g. when the value of a selector property has changed or a node has been moved into a different site. The value of this generic contnet URL protocol is ``dummy-wildcard-store-protocol://``, and its use will typically match the following pattern:

* Scenario: Routing content store A needs to copy a piece of content from its backing store A1 to A2 - store A1 uses the content URL protocol ``store-A1://``, A2 ``store-A2://``, and the piece of content currently has the content URL ``store-A1://2019/1/1/12/31/0123-abcd-1234-12341234abcd.bin``
* Requirement: Store A needs to check A2 if it already contains the piece of content before copying, but cannot assume the same content URL protocol is in use, and also wants to force the new content URL to contain the same URL path fragments

1. Store A constructs a new content URL using the same URL path but replacing the protocol with the wildcard protocol
2. Store A calls ``ContentStore#isContentUrlSupported(String)`` on A2, using the wildcard content URL
3. Store A2 calls ``ContentUrlUtils#checkAndReplaceWildcardProtocol(String, String)`` using its content URL protocols as the second parameter
4. Store A2 checks that the resolved, effective content URL uses its configured protocol, and reports the URL to be supported
5. Store A calls ``ContentStore#exists(String)`` on A2 to check if the piece of content already exists, using the wildcard content URL
6. Store A2 again resolves the effective content URL
7. Store A2 checks its content structure and does not find a matching file
8. Store A calls ``ContentStore#getWriter(ContentContext)``, setting the wildcard content URL as the requested content URL inside the context parameter
9. Store A2 again resolves the effective content URL
10. Store A2 initialises an empty file, using the path specified by the path segments of the content URL
11. Store A2 constructs a content writer instance using the resolved, effective content URL
12. Store A puts the content from store A1 into the writer from store A2
13. Store A uses the result of ``ContentAccessor#getContentUrl()`` on the writer from store A2 to update the content data of the affected node
14. Since the original content URL is no longer referenced by an active node (the new URL uses a different protocol) it is marked as orphaned and thus eligible for future cleanup

## Changes to Default Alfresco Spring Beans

In order to tie this addon into the Alfresco Content Service <-> Content Store integration, and provide the necessary context information for some specific implementations, this addon applies the following modifications to default Alfresco Spring beans.

### Alfresco 4.2

TBD

### Alfresco 5.0 and above
 
1. If _simpleContentStores.enabled_ is set to _true_, modify the application context-level _ContentStore_ factory bean to explicitly use the _simpleContentStores_ ContentStore subsystem variant - unless the specified class of the bean already differs from the default Alfresco class, indicating the presence of a conflicting customisation.
2. If _simpleContentStores.enabled_ is set to _true_, register an additional service interceptor on the public _ContentService_ bean, capturing parameters of a call to ```ContentService#getReader(NodeRef, QName)``` as context information for interested content store implementations (default Alfresco APIs only pass a content URL to stores, which can be insufficient in some cases)

**Note:** Since some content stores provided by this addon may depend on the additional context information provided by the interceptor listed as the 2nd change to default Alfresco beans, it is **highly recommended** for developers and administrators who want to use this addon, that all calls to the Alfresco _ContentStore_ access the public service bean, and not the lower-cased _contentService_ implementation bean.