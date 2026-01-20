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

import java.util.Collection;

import org.alfresco.repo.cache.SimpleCache;
import org.alfresco.repo.cache.lookup.EntityLookupCache;
import org.alfresco.repo.cache.lookup.EntityLookupCache.EntityLookupCallbackDAOAdaptor;
import org.alfresco.repo.domain.contentdata.ContentDataDAO;
import org.alfresco.repo.domain.contentdata.ContentUrlEntity;
import org.alfresco.repo.domain.contentdata.ContentUrlUpdateEntity;
import org.alfresco.util.Pair;
import org.alfresco.util.ParameterCheck;
import org.alfresco.util.PropertyCheck;
import org.alfresco.util.transaction.TransactionSupportUtil;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.InitializingBean;

/**
 *
 * @author Axel Faust
 */
public class ContentUrlConsistencyDAOImpl implements ContentUrlConsistencyDAO, InitializingBean
{

    // copied from ContentDataDAOImpl
    private static final String SELECT_CONTENT_URL_BY_KEY_UNREFERENCED = "alfresco.content.select_ContentUrlByKeyUnreferenced";

    // copied from ContentDataDAOImpl
    private static final String UPDATE_CONTENT_URL_ORPHAN_TIME = "alfresco.content.update_ContentUrlOrphanTime";

    // copied from AbstractContentDataDAOImpl
    private static final String CACHE_REGION_CONTENT_URL = "ContentUrl";

    // copied from TransactionSupportUtil
    private static final String RESOURCE_KEY_TXN_ID = "AlfrescoTransactionSupport.txnId";

    // cannot re-use standard callback DAO due to visibility, so this is our own (simplified) variant
    private final ContentUrlCallbackDAO contentUrlCallbackDAO = new ContentUrlCallbackDAO();

    private SqlSessionTemplate template;

    private ContentDataDAO contentDataDAO;

    private EntityLookupCache<Long, ContentUrlEntity, String> contentUrlCache;

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet() throws Exception
    {
        PropertyCheck.mandatory(this, "template", this.template);
        PropertyCheck.mandatory(this, "contentDataDAO", this.contentDataDAO);
        PropertyCheck.mandatory(this, "contentUrlCache", this.contentUrlCache);
    }

    /**
     * @param sqlSessionTemplate
     *     the sqlSessionTemplate to set
     */
    public void setSqlSessionTemplate(final SqlSessionTemplate sqlSessionTemplate)
    {
        this.template = sqlSessionTemplate;
    }

    /**
     * @param contentDataDAO
     *     the contentDataDAO to set
     */
    public void setContentDataDAO(final ContentDataDAO contentDataDAO)
    {
        this.contentDataDAO = contentDataDAO;
    }

    /**
     * @param contentUrlCache
     *     the contentUrlCache to set
     */
    public void setContentUrlCache(final SimpleCache<Long, ContentUrlEntity> contentUrlCache)
    {
        this.contentUrlCache = new EntityLookupCache<>(contentUrlCache, CACHE_REGION_CONTENT_URL, this.contentUrlCallbackDAO);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Pair<ContentUrlEntity, Boolean> getContentUrlEntityUnreferenced(final String contentUrl)
    {
        final ContentUrlEntity entity = this.contentDataDAO.getContentUrl(contentUrl);
        if (entity == null)
        {
            return null;
        }

        ContentUrlEntity contentUrlEntity = new ContentUrlEntity();
        contentUrlEntity.setContentUrl(contentUrl);
        if (contentUrlEntity.getContentUrlShort() != null)
        {
            contentUrlEntity.setContentUrlShort(contentUrlEntity.getContentUrlShort().toLowerCase());
        }
        contentUrlEntity = (ContentUrlEntity) this.template.selectOne(SELECT_CONTENT_URL_BY_KEY_UNREFERENCED, contentUrlEntity);

        return new Pair<>(entity, contentUrlEntity != null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean unoprhanContentUrl(final Long id, final Long oldOrphanTime)
    {
        final ContentUrlUpdateEntity contentUrlUpdateEntity = new ContentUrlUpdateEntity();
        contentUrlUpdateEntity.setId(id);
        contentUrlUpdateEntity.setOrphanTime(null);
        contentUrlUpdateEntity.setOldOrphanTime(oldOrphanTime);
        return 1 == this.template.update(UPDATE_CONTENT_URL_ORPHAN_TIME, contentUrlUpdateEntity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invalidateCachedContentUrlEntity(final String contentUrl)
    {
        ParameterCheck.mandatoryString("contentUrl", contentUrl);

        final ContentUrlEntity value = new ContentUrlEntity();
        value.setContentUrl(contentUrl);
        this.withTxnCacheContext(() -> this.contentUrlCache.removeByValue(value));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invalidateCachedContentUrlEntities(final Collection<String> contentUrls)
    {
        ParameterCheck.mandatoryCollection("contentUrls", contentUrls);

        final ContentUrlEntity value = new ContentUrlEntity();
        this.withTxnCacheContext(() -> {
            for (final String contentUrl : contentUrls)
            {
                value.setContentUrl(contentUrl);
                this.contentUrlCache.removeByValue(value);
            }
        });
    }

    private void withTxnCacheContext(final Runnable run)
    {
        // remove in afterCommit does not do anything since TransactionalCache blocks it as it already ran through its sync
        // technically, no transaction is ongoing here, but transaction ID is still bound, suggesting it still is active
        // so we have to hide it (temporarily) - a lesson learned from Aldica

        final long txnStartTime = TransactionSupportUtil.getTransactionStartTime();
        final String txnId = TransactionSupportUtil.getTransactionId();
        final boolean inAfterCompletion = txnStartTime == -1 && txnId != null;
        if (inAfterCompletion)
        {
            TransactionSupportUtil.unbindResource(RESOURCE_KEY_TXN_ID);
        }
        try
        {
            run.run();
        }
        finally
        {
            if (inAfterCompletion)
            {
                TransactionSupportUtil.bindResource(RESOURCE_KEY_TXN_ID, txnId);
            }
        }
    }

    private static class ContentUrlCallbackDAO extends EntityLookupCallbackDAOAdaptor<Long, ContentUrlEntity, String>
    {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getValueKey(final ContentUrlEntity value)
        {
            return value.getContentUrl();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Pair<Long, ContentUrlEntity> findByValue(final ContentUrlEntity entity)
        {
            throw new UnsupportedOperationException("Callback should not be used to find entities");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Pair<Long, ContentUrlEntity> createValue(final ContentUrlEntity value)
        {
            throw new UnsupportedOperationException("Callback should not be used to create entities");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Pair<Long, ContentUrlEntity> findByKey(final Long id)
        {
            throw new UnsupportedOperationException("Callback should not be used to find entities");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int updateValue(final Long id, final ContentUrlEntity value)
        {
            throw new UnsupportedOperationException("Callback should not be used to update entities");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int deleteByKey(final Long id)
        {
            throw new UnsupportedOperationException("Callback should not be used to delete entities");
        }
    }

}
