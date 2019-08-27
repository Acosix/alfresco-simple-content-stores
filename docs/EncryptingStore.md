# Encrypting Content Store

The store type **_encryptingFacadeStore_** implemented by the class _de.acosix.alfresco.simplecontentstores.repo.store.facade.EncryptingContentStore_ acts as a facade to other stores, transparently en-/decrypting content during write and read operations. This store is only supported on Alfresco 5.0 and above, since the required database table used to store encryption keys for individual content files are not available in earlier releases. Similar to the Alfresco Enterprise Encrypted Content Store, this store uses symmetric encryption to protect content files, but uses a unique encryption key for each file. The symmetric encryption keys are in turn encrypted using an asymmetric algorithm, and stored in the database associated with the content URL they protect. That way, even if a single encryption key may have been compromised / broken, all other content files are still reasonably secure, requiring the same amount of effort use for compromising / breaking the first key to compromise / break each subsequent key, and not compromising all keys at once.

## Relation with other stores

This type of store can only be used as a facade to a single, other stores. Any stores in which content processed by this facade may end up being stored should not be accessible via any path of configured content stores which does not also include this facade. Otherwise encrypted content may be accidentally exposed without the appropriate, transparent decryption, leaving content inaccessible / unusable.

Multiple instances of this type of store can be configured to set up encryption for multiple storage paths. However, care must be taken to set up all stores to avoid conflicts / side effects. It **not supported** to configure two or more instances in such a way that they may both/all be part of the chain of stores to read / write a particular content file, i.e. to have encryption applied more than once on a content file. The Alfresco database only allows for one encryption key to be stored per content URL, and when two or more encryption steps are applied to the same content file, one or more of the encryption keys are unavoidably lost during the process. Additionally, it is **recommended** to use distinct master key aliases and store IDs in the configuration when setting up multiple instances. The aliases and IDs are also stored alongside the encryption keys. Having separate values allows for an implicit validation that the encryption key associated with a particular content URL has actually been created by the content store asked to decrypt it, forming another layer of protection against accidental exposure of encrypted file contents.

While technically supported, there would be little use in setting up either a compressing or deduplicating content stores as direct or indirect _backingStore_ instances to an instance of this type of store. The individual encryption of each content file destroys any chance at deduplicating existing content, and also strips any compressable file of the key characteristics that make compression efficient. Any compressing or deduplicating stores must therefore be set up in such way an encrypting store is **the last store** in the chain of stores that may transform the content before being written to the actual storage.

## Preparing the Master Key

Each instance of this type of content store requires access to a keystore which contains the master key it should use for encrypting/decrypting the symmetric encryption keys for each content file. It is possible to use the same keystore for multiple content stores, using key aliases to keep the individual keys separate. A new master key can be generated using the Java _keytool_ binary, using the following command:

```text
keytool -genkey -alias &lt;alias for identification&gt; -keyalg RSA -keystore &lt;master keystore path&gt; -keysize &lt;desired key length&gt;
```

The alias of the generated key can be any value, as long as it is not longer than 15 characters - a limit imposed by the Alfresco database schema.

## Configuration Properties

This store can be selected by using the store type **_encryptingFacadeStore_**.

| name | type | description | default | optional |
| :---| :--- | :--- | :--- | :--- |
| backingStore | ref | the store via which the content should be further processed and eventually stored | | no |
| keyStorePath | value | the path to the Java keystore file holding an asymmetric master key (used for encrypting the symmetric keys that de-/encrypt content files) - this can take any path expression supported by Spring, e.g. classpath:path/to/keystore.jks |  | no |
| keyStoreType | value | the type / format of the Java keystore file | (dependent on JDK default - typically ``JKS``) | no |
| keyStoreProvider | value | the name of the provider capable of handling the Java keystore type / format |  | yes |
| keyStorePassword | value | the password used to access the Java keystore file containing the asymmetric master key |  | yes |
| masterKeyAlias | value | the alias referencing the asymmetric master key within the Java keystore file - must be at most 15 characters in length |  | no |
| masterKeyPassword | value | the password used to access the asymmetric master key within the Java keystore file |  | yes |
| keyAlgorithm | value | the symmetric key algorithm used to generate content encryption keys - this algorithm must support encryption in cipher block chaining mode with PKCS5Padding | AES | no |
| keyAlgorithmProvider | value | the name of the algorithm provider to be used for generating the content encryption keys |  | yes |
| keySize | value | the size (in bits) to be used when generating content encryption keys | 128 | no |
| masterKeyStoreId | value | the static "masterKeyStore" ID to uniquely group all the content encryption keys generated by this store in the Alfresco database  - must not be longer than 20 characters |  | no |
| masterKeySize | value | (informational value) the size of the asymmetric master key in bits - this is primarily used to optimise encryption of the symmetric content encryption keys | ``4096`` | yes |

## Configuration example

```text
simpleContentStores.enabled=true

simpleContentStores.customStores=myEncryptingStore,defaultTenantFileContentStore
simpleContentStores.rootStore=myEncryptingStore

simpleContentStores.customStore.myEncryptingStore.type=encryptingFacadeStore
simpleContentStores.customStore.myEncryptingStore.ref.backingStore=defaultTenantFileContentStore
simpleContentStores.customStore.myEncryptingStore.value.keyStorePath=classpath:keystore.jks
simpleContentStores.customStore.myEncryptingStore.value.keyStorePassword=password1234
simpleContentStores.customStore.myEncryptingStore.value.masterKeyAlias=myRsaKey
simpleContentStores.customStore.myEncryptingStore.value.masterKeyPassword=password1234
simpleContentStores.customStore.myEncryptingStore.value.masterKeyStoreId=SimpleContentStores
```