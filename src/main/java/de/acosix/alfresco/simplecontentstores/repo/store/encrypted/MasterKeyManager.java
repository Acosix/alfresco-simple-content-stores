/*
 * Copyright 2017 - 2022 Acosix GmbH
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

import java.util.Collection;
import java.util.Map;

/**
 * Instances of this interface manage the state of master keys used for encrypting symmetric encryption keys for content URL entities.
 *
 * @author Axel Faust
 */
public interface MasterKeyManager
{

    /**
     * Checks whether this instance has been activated, i.e. at least one encrypting content store has been set up and in turn initialised
     * the state of this instance.
     *
     * @return {@code true} if this instance has been activated, {@code false} otherwise
     */
    boolean hasBeenActivated();

    /**
     * Checks whether a particular encryption master key supports decryption of symmetric content encryption keys, even if it may not be
     * {@link #getActiveKeys() active}.
     *
     * @param masterKey
     *            the master key to check
     * @return {@code true} if the key is available for decryption
     */
    boolean supportsDecryption(MasterKeyReference masterKey);

    /**
     * Retrieves the encryption master keys which have been recorded in the database or are being used on other Alfresco servers in the same
     * cluster, but whose algorithm and/or key hash are not matching with the key(s) on this particular server.
     *
     * @param current
     *            {@code true} if only those keys missing from a comparison with cluster nodes should be returned, {@code false} from a
     *            comparison with database-recorded keys
     * @return the collection of master keys
     */
    Collection<MasterKeyReference> getMismatchedKeys(boolean current);

    /**
     * Retrieves the encryption master keys which have been recorded in the database or are being used on other Alfresco servers in the same
     * cluster, which are not available on this particular server.
     *
     * @param current
     *            {@code true} if only those keys missing from a comparison with cluster nodes should be returned, {@code false} from a
     *            comparison with database-recorded keys
     * @return the collection of master keys
     */
    Collection<MasterKeyReference> getMissingKeys(boolean current);

    /**
     * Retrieves the encryption master keys which are available on this particular server but not on any other server in an Alfresco
     * cluster.
     *
     * @return the collection of master keys
     */
    Collection<MasterKeyReference> getExtraneousKeys();

    /**
     * Retrieves the encryption master keys which have can be used in encryption of contents.
     *
     * @return the collection of master keys
     */
    Collection<MasterKeyReference> getActiveKeys();

    /**
     * Retrieves the encryption master keys which have been disabled with regards to usage in any future encryption of contents (may still
     * be used for decryption of existing content).
     *
     * @return the collection of master keys
     */
    Collection<MasterKeyReference> getDisabledKeys();

    /**
     * Retrieves the number of symmetric content encryption keys which have been encrypted by specific master keys.
     *
     * @return the count of symmetric encryption keys encrypted by the respective master key
     */
    Map<MasterKeyReference, Integer> countEncryptedSymmetricKeys();

    /**
     * Retrieves the number of symmetric content encryption keys which have been encrypted with the specified master key.
     *
     * @param masterKey
     *            the master key for which to count the symmetric encryption keys
     * @return the count of symmetric encryption keys encrypted using the master key
     */
    int countEncryptedSymmetricKeys(MasterKeyReference masterKey);

    /**
     * Retrieves the inactive encryption master keys for which symmetric content encryption keys exist that still have to be re-encrypted.
     *
     * @return the collection of master keys
     */
    Collection<MasterKeyReference> getKeysRequiringReEncryption();

    /**
     * Enables a specific key, (re-)allowing its use in future content encryptions.
     *
     * @param masterKey
     *            the key to enable
     */
    void enable(MasterKeyReference masterKey);

    /**
     * Disables a specific key, preventing its use in future content encryptions.
     *
     * @param masterKey
     *            the key to disable
     */
    void disable(MasterKeyReference masterKey);
}
