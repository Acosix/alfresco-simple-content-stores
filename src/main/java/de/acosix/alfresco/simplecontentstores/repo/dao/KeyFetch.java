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
package de.acosix.alfresco.simplecontentstores.repo.dao;

import de.acosix.alfresco.simplecontentstores.repo.store.encrypted.MasterKeyReference;

/**
 * @author Axel Faust
 */
public class KeyFetch
{

    private MasterKeyReference masterKey;

    private Long fromId;

    private Integer maxItems;

    /**
     * @return the masterKey
     */
    public MasterKeyReference getMasterKey()
    {
        return this.masterKey;
    }

    /**
     * @param masterKey
     *            the masterKey to set
     */
    public void setMasterKey(final MasterKeyReference masterKey)
    {
        this.masterKey = masterKey;
    }

    /**
     * @return the fromId
     */
    public Long getFromId()
    {
        return this.fromId;
    }

    /**
     * @param fromId
     *            the fromId to set
     */
    public void setFromId(final Long fromId)
    {
        this.fromId = fromId;
    }

    /**
     * @return the maxItems
     */
    public Integer getMaxItems()
    {
        return this.maxItems;
    }

    /**
     * @param maxItems
     *            the maxItems to set
     */
    public void setMaxItems(final Integer maxItems)
    {
        this.maxItems = maxItems;
    }

}
