/*
 * Copyright 2017 - 2024 Acosix GmbH
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
package de.acosix.alfresco.simplecontentstores.repo.dao;

import java.util.List;
import java.util.Map;

import org.alfresco.repo.domain.contentdata.ContentDataDAO;
import org.alfresco.repo.domain.contentdata.ContentUrlKeyEntity;

import de.acosix.alfresco.simplecontentstores.repo.store.encrypted.MasterKeyReference;

/**
 * Instances of this interface provide more fine-grained, read-only access to persisted content URL encryption keys than is possible via the
 * {@link ContentDataDAO default content data DAO}. Specifically, instances support querying not only on encryption key aliases, but also
 * keystore IDs, a data attribute already collected in {@link ContentUrlKeyEntity the content URL key entity} in Alfresco but omitted in
 * e.g. {@link ContentDataDAO#countSymmetricKeysForMasterKeyAlias(String) counting keys per alias}.
 *
 * @author Axel Faust
 */
public interface ContentUrlKeyDAO
{

    /**
     * Counts symmetric key entities for all master encryption keys. The result will only contain entries for master keys that have been
     * used to encrypt at least one content object.
     *
     * @return the number symmetric encryption keys stored as encrypted by the various master keys
     */
    Map<MasterKeyReference, Integer> countSymmetricKeys();

    /**
     * Counts symmetric key entities for a particular master encryption key.
     *
     * @param masterKey
     *            the identitiy of the master key for which to count the symmetric keys
     * @return the number symmetric encryption keys stored as encrypted by the master key
     */
    int countSymmetricKeys(MasterKeyReference masterKey);

    /**
     * Retrieves a page of symmetric key entities for a particular master encryption key.
     *
     * @param masterKey
     *            the identity of the master key for which to retrieve the symmetric keys
     * @param fromId
     *            the exclusive, lower bound of entity IDs to retrieve
     * @param maxResults
     *            the upper limit of number of entities to retrieve
     * @return the page of entities
     */
    List<ContentUrlKeyEntity> getSymmetricKeys(MasterKeyReference masterKey, Long fromId, Integer maxResults);
}
