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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alfresco.repo.domain.contentdata.ContentUrlKeyEntity;
import org.alfresco.util.ParameterCheck;
import org.alfresco.util.PropertyCheck;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.InitializingBean;

import de.acosix.alfresco.simplecontentstores.repo.store.encrypted.MasterKeyReference;

/**
 * @author Axel Faust
 */
public class ContentUrlKeyDAOImpl implements ContentUrlKeyDAO, InitializingBean
{

    private static final String SELECT_COUNT_SYMMETRIC_KEYS_BY_MASTER_KEY = "contentUrlKey.select_CountSymmetricKeysByMasterKey";

    private static final String SELECT_COUNT_SYMMETRIC_KEYS_FOR_MASTER_KEY = "contentUrlKey.select_CountSymmetricKeysForMasterKey";

    private static final String SELECT_SYMMETRIC_KEYS_FOR_MASTER_KEY = "contentUrlKey.select_SymmetricKeysForMasterKey";

    protected SqlSessionTemplate sqlSessionTemplate;

    /**
     * @param sqlSessionTemplate
     *            The SQL session template to set
     */
    public void setSqlSessionTemplate(final SqlSessionTemplate sqlSessionTemplate)
    {
        this.sqlSessionTemplate = sqlSessionTemplate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "sqlSessionTemplate", this.sqlSessionTemplate);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<MasterKeyReference, Integer> countSymmetricKeys()
    {
        final List<KeyCount> counts = this.sqlSessionTemplate.selectList(SELECT_COUNT_SYMMETRIC_KEYS_BY_MASTER_KEY);
        final Map<MasterKeyReference, Integer> countMap = new HashMap<>();
        counts.forEach(count -> countMap.put(count.getMasterKey(), count.getCount()));
        return countMap;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int countSymmetricKeys(final MasterKeyReference masterKey)
    {
        ParameterCheck.mandatory("masterKey", masterKey);
        final Integer count = this.sqlSessionTemplate.selectOne(SELECT_COUNT_SYMMETRIC_KEYS_FOR_MASTER_KEY, masterKey);
        return count;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ContentUrlKeyEntity> getSymmetricKeys(final MasterKeyReference masterKey, final Long fromId, final Integer maxResults)
    {
        ParameterCheck.mandatory("masterKey", masterKey);

        final KeyFetch fetch = new KeyFetch();
        fetch.setMasterKey(masterKey);
        if (fromId != null)
        {
            fetch.setFromId(fromId);
        }
        if (maxResults != null && maxResults.intValue() > 0)
        {
            fetch.setMaxItems(maxResults);
        }

        final List<ContentUrlKeyEntity> entities = this.sqlSessionTemplate.selectList(SELECT_SYMMETRIC_KEYS_FOR_MASTER_KEY, fetch);
        return entities;
    }

}
