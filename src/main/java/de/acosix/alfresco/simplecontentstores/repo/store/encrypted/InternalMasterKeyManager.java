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

import org.alfresco.util.Pair;

/**
 *
 * @author Axel Faust
 */
public interface InternalMasterKeyManager extends MasterKeyManager
{

    /**
     * Activates this instance, loading any master keys configured for use.
     */
    void activate();

    /**
     * Performs any on startup validation / handling required for this instance.
     */
    void onStartup();

    /**
     * Selects a random active master key for encryption of data.
     *
     * @return the pair of master key identifier and actual encryption key
     */
    Pair<MasterKeyReference, Key> getRandomActiveEncryptionKey();

    /**
     * Retrieves the master key for decryption of data.
     *
     * @param masterKey
     *            the identity of the master key for which to retrieve the decryption key
     * @return the master decryption key
     */
    Optional<Key> getDecryptionKey(MasterKeyReference masterKey);
}
