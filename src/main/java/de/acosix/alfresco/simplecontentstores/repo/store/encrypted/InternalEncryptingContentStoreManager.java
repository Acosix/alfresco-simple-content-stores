/*
 * Copyright 2017 - 2021 Acosix GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.acosix.alfresco.simplecontentstores.repo.store.encrypted;

import java.security.Key;
import java.util.Optional;

import javax.crypto.spec.SecretKeySpec;

import org.alfresco.service.cmr.repository.ContentIOException;

/**
 * @author Axel Faust
 */
public interface InternalEncryptingContentStoreManager extends EncryptingContentStoreManager
{

    /**
     * Activates this instance, loading any master keys configured for use.
     */
    void activate();

    /**
     * Retrieves the symmetric content decryption key for a particular content URL.
     *
     * @param contentUrl
     *            the URL of the content object for which to retrieve the decryption key
     * @return the decryption key to use if the particular content is actually encrypted, otherwise an unresolved value
     * @throws ContentIOException
     *             if either the URL is not associated with any stored content or the stored key was encrypted with an unavailable master
     *             key
     */
    Optional<SecretKeySpec> getDecryiptionKey(String contentUrl);

    /**
     * Creates a new symmetric content encryption key.
     *
     * @param keySize
     *            the size of the key - if {@code 0} or less, a default key size will be used
     * @return the symmetric encryption key
     */
    Key createEncryptionKey(int keySize);

    /**
     * Creates a new symmetric content encryption key.
     *
     * @param keySize
     *            the size of the key - if {@code 0} or less, a default key size will be used
     * @param algorithm
     *            the name of the encryption algorithm to use
     * @return the symmetric encryption key
     */
    Key createEncryptionKey(int keySize, String algorithm);

    /**
     * Creates a new symmetric content encryption key.
     *
     * @param algorithm
     *            the name of the encryption algorithm to use
     * @return the symmetric encryption key
     */
    Key createEncryptionKey(String algorithm);

    /**
     * Stores the symmetric encryption key used to encrypt a particular content in the associated content URL entity.
     *
     * @param contentUrl
     *            the URL of the content with which to associate the encryption key
     * @param fileSize
     *            the size of the unencrypted file to be stored in the content url key entity
     * @param encryptedfileSize
     *            the size of the encrypted file to be stored in the content url entity
     * @param encryptionKey
     *            the key to store
     */
    void storeEncryptionKey(String contentUrl, long fileSize, long encryptedfileSize, Key encryptionKey);
}
