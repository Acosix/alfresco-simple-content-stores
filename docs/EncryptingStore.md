# Encrypting Content Store

The store type **_encryptingFacadeStore_** implemented by the class _de.acosix.alfresco.simplecontentstores.repo.store.encrypted.EncryptingContentStore_ acts as a facade to other stores, transparently en-/decrypting content during write and read operations. This store is only supported on Alfresco 5.0 and above, since the required database table used to store encryption keys for individual content files are not available in earlier releases. Similar to the Alfresco Enterprise Encrypted Content Store, this store uses symmetric encryption to protect content files, but uses a unique encryption key for each file. The symmetric encryption keys are in turn encrypted using either an asymmetric or symmetric (but with a substantially more secure encryption key) algorithm, and stored in the database associated with the content URL they protect. That way, even if a single encryption key may have been compromised / broken, all other content files are still reasonably secure, requiring the same amount of effort use for compromising / breaking the first key to compromise / break each subsequent key, and not compromising all keys at once.

## Relation with other stores

This type of store can only be used as a facade to a single, other stores. Any stores in which content processed by this facade may end up being stored should not be accessible via any path of configured content stores which does not also include this facade. Otherwise encrypted content may be accidentally exposed without the appropriate, transparent decryption, leaving content inaccessible / unusable.

Multiple instances of this type of store can be configured to set up encryption for multiple storage paths. However, care must be taken to set up all stores to avoid conflicts / side effects. It is **not supported** to configure two or more instances in such a way that they may both/all be part of a directed chain of stores to read / write a particular content file, i.e. to have encryption applied more than once on a content file. The Alfresco database only allows for one encryption key to be stored per content URL, and when two or more encryption steps are applied to the same content file, one or more of the encryption keys are unavoidably lost during the process.

It is **not supported** to configure an instance which sits in front of a routing content store that may move content between different backing stores on updates to the node. More generally, it is not supported that a direct or indirect backing content store copies / relocates content in such a way that a new content URL is created, as the new content URL would not be associated with the encryption key and thus the content would not be able to be decrypted in any future read access. With regards to the stores currently provided by this module, the following types cannot be used as (in)direct backing stores:

- **_siteRoutingStore_**
- **_siteRoutingFileStore_**
- **_typeRoutingStore_**
- **_selectorPropertyRoutingStore_**

It is **not supported** to configure an instance as a direct or indirect backing store to a store that may modify the content URL in any way after the content is written to disk, e.g. by injecting additional path elements for differentiation in routing stores or similar. Similar to the last point, doing so would create a new content URL which would not be associated with the encryption key and thus the content would not be able to be decrypted in any future read access. No content store facade variant currently provided by this module acts in such a way, so all facade variants may use an instance of this type as the backing store. Only the terminal **_siteRoutingFileStore_** uses additional path elements in a content URL to transport routing information, and it cannot be backed by another store.

While technically supported, there would be little use in setting up either a compressing or deduplicating content stores as direct or indirect _backingStore_ instances to an instance of this type of store. The individual encryption of each content file eliminates any chance at deduplicating existing content, and also strips any compressable file of the key characteristics that make compression efficient. Any compressing or deduplicating stores must therefore be set up in such way an encrypting store is **the last store** in the chain of stores that may transform the content before being written to the actual storage.

## Master Key Management

Since version 1.4.0 of this module, all master encryption keys used to encrypt the symmetric encryption keys of individual content URLs are centrally configured and managed. It is no longer possible to configure separate keystores and key aliases per store instance. In turn, the central management supports configuration of multiple keystores and key aliases, and selects a random key every time a symmetric encryption key needs to be encrypted. That way, security is further increased by ensuring that even in the worst case of a compromised master key  and full access to all encrypted symmetric keys in the database, only a fraction of content files may be accessible, with the size of the fraction dependent on the number of master keys available to the system.

Additionally, since version 1.4.0, every ACS instance which has at least one encrypted content store configured to be used, will check the available master keys against simple check values/signatures of keys that have been used to in the past to encrypt symmetric keys, as well of keys configured in other cluster nodes, if the ACS instance is part of a cluster at all. By default, if any keys are missing or mismatched, the ACS instance will terminate startup and prevent operation in a mode in which access to content may be partially defunct.

### Preparing Master Keys

A new asymmetric master key can be generated using the Java _keytool_ binary, using the following command:

```text
keytool -genkey -alias &lt;alias for identification&gt; -keyalg RSA -keystore &lt;master keystore path&gt; -keysize &lt;desired key length&gt;
```

This will generate both private and public keys for the asymmetric encryption of symmetric keys in the keystore. Alternatively, a sufficiently secure symmetric encryption key may also be used. E.g. a new AES key can be generated using the following command:

```text
keytool -genkey -alias &lt;alias for identification&gt; -keyalg AES -keystore &lt;master keystore path&gt; -keysize 128
```

The alias of the generated key can be any value, as long as it is not longer than 15 characters - a limit imposed by the Alfresco database schema. Multiple keys can be contained in a single keystore, and multiple keystores may be used to easily rotate in/out groups of keys when necessary.

### Keystore Configuration

The configuration of the location and password of keystores, as well as aliases and passwords of keys contained therein, uses a configuration approach similar to what Alfresco uses beginning with ACS 6.2.x service packs and in general since ACS 7.0. In contrast to Alfresco's configuration approach, it is left up to the administrator to choose between configuration via properties files or Java system properties. Additionally, the approach used by this module supports loading passwords from mounted secrets without actually exposing them as configuration or system properties.

The precedence rules for any keystore configuration are as follows;

- subsystem properties before global properties
- global properties before system properties
- `xxx.password.location` before `xxx.password` properties

All keystore-related configuration properties use the common prefix `simpleContentStores.encryption.`, with the following suffixes / suffix patterns for specific uses:

| Suffix | Description |
| :-- | :--- |
| keystore.&lt;keystoreId&gt;.location | the path to the keystore file - **must** be set |
| keystore.&lt;keystoreId&gt;.password | the password to access the keystore |
| keystore.&lt;keystoreId&gt;.password.location | the path to a mounted secret / file containing the password to access the keystore |
| keystore.&lt;keystoreId&gt;.type | the type of the keystore - **must** be set |
| keystore.&lt;keystoreId&gt;.provider | the name of the provider for the keystore type implementation |
| keystore.&lt;keystoreId&gt;.aliases | the comma-separated list of aliases for keys to load from the keystore - **must** be set |
| keystore.&lt;keystoreId&gt;.&lt;alias&gt;.password | the password to access the key |
| keystore.&lt;keystoreId&gt;.&lt;alias&gt;.password.location | the path to a mounted secret / file containing the password to access the key |

### Other Centralised Configuration

In addition to keystore configuration, this module provides the following encryption-related configuration properties to be specified via subsystem / global properties (defaults in paranthesis).

- `simpleContentStores.encryption.defaultSymmetricKeyAlgorithm` (AES) - the algorithm to use for generating symmetric encryption keys for new content (may be overridden on a per-content-store basis)
- `simpleContentStores.encryption.defaultSymmetricKeySize` (128) - the size of the symmatric encryption key to generate
- `simpleContentStores.encryption.reencryption.threadCount` (4) - the number of threads to use when running a re-encryption process for deactivated master encryption keys
- `simpleContentStores.encryption.reencryption.batchSize` (50) - the number of symmetric encryption keys to re-encrypt in a single transaction / batch when running a re-encryption process for deactivated master encryption keys
- `simpleContentStores.encryption.reencryption.logInterval` (1000) - the number of re-encrypted symmetric encryption keys after which to log progress messages when running a re-encryption process for deactivated master encryption keys
- `simpleContentStores.encryption.validation.failMissingDatabaseKeys` (true) - flag to toggle startup failure if any keys are detected to be missing in keystores which were used to encrypt symmetric keys of existing content
- `simpleContentStores.encryption.validation.failMissingClusterKeys` (true) - flag to toggle startup failure if any keys are detected to be missing in keystores which have been configured on other ACS servers with which "this" instance of ACS has formed a cluster
- `simpleContentStores.encryption.validation.failMismatchedDatabaseKeys` (true) - flag to toggle startup failure if any keys are detected to not be a match to keys used to encrypt symmetric keys of existing content
- `simpleContentStores.encryption.validation.failMismatchedClusterKeys` (true) - flag to toggle startup failure if any keys are detected to not be a match to keys which have been configured on other ACS servers with which "this" instance of ACS has formed a cluster

### Persistence

The following information / data about master keys is stored in a persistent form within Alfresco:

- **disabled master keys**: Alfresco's `AttributeService` is used to store disabled master keys using the keys `acosix/alfresco-simple-content-stores/disabledEncryptionMasterKeys`, `<keystoreId>`, `<alias>` - a master key disabled once will remain disabled until re-enabled or the attribute entry is otherwise deleted (while ACS is not active)
- **master key check values**: whenever a master key is first randomly picked to encrypt a symmetric encryption key, its "check value" is stored via Alfresco's `AttributeService` for future key validation on startup, using the keys `acosix/alfresco-simple-content-stores/encryptionMasterKeyCheckValues`, `<keystoreId>`, `<alias>` - the "check value" is essentially just a string in the form of `<keyAlgorithm>#<encodedKeyHashCode>`, so that the persistent form does not contain any sensitive information but can still be used to check if keys in the keystore match keys that have been previously used to have early detection of mismatching keys

### OOTBee Support Tools Command Console Plugin

In order to expose the runtime-administration capabilities to enable / disable / re-encrypt, this module includes a plugin extension to the [Command Console tool](https://github.com/OrderOfTheBee/ootbee-support-tools/wiki/Command-Console) of the [OOTBee Support Tools addon](https://github.com/OrderOfTheBee/ootbee-support-tools). When both modules are installed, the administrative actions can be accessed by going to e.g. `<host>/alfresco/s/ootbee/admin/command-console` and then using the `activatePlugin simple-content-stores` command. Once activated, the following commands are available:

- `listEncryptionKeys <active|inactive>`
- `enableEncryptionKey <masterKey>`
- `disableEncryptionKey <masterKey>`
- `countEncryptedSymmetricKeys <masterKey>`
- `listEncryptionKeysEligibleForReEncryption`
- `reEncryptSymmetricKeys <masterKey>`

## Configuration Properties

This store can be selected by using the store type **_encryptingFacadeStore_**.

| name | type | description | default | optional |
| :---| :--- | :--- | :--- | :--- |
| backingStore | ref | the store via which the content should be further processed and eventually stored | | no |
| keyAlgorithm | value | the symmetric key algorithm used to generate content encryption keys |  | yes |
| keySize | value | the size (in bits) to be used when generating content encryption keys |  | yes |

## Configuration example

```text
simpleContentStores.enabled=true

simpleContentStores.customStores=myEncryptingStore,defaultTenantFileContentStore
simpleContentStores.rootStore=myEncryptingStore

simpleContentStores.encryption.keystoreIds=primary-ks
simpleContentStores.encryption.keystore.primary-ks.location=classpath:keystore.jks
simpleContentStores.encryption.keystore.primary-ks.password=password1234
simpleContentStores.encryption.keystore.primary-ks.aliases=firstKey,secondKey
simpleContentStores.encryption.keystore.primary-ks.firstKey.password=l33tp455w0rd
simpleContentStores.encryption.keystore.primary-ks.secondKey.password.location=/run/secrets/second-key-password

simpleContentStores.customStore.myEncryptingStore.type=encryptingFacadeStore
simpleContentStores.customStore.myEncryptingStore.ref.backingStore=defaultTenantFileContentStore
```