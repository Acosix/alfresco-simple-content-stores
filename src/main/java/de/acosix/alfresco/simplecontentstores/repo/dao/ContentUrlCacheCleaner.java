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
import java.util.Set;

import org.alfresco.repo.cache.SimpleCache;
import org.alfresco.repo.cache.lookup.EntityLookupCache;
import org.alfresco.repo.cache.lookup.EntityLookupCache.EntityLookupCallbackDAOAdaptor;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.content.cleanup.ContentStoreCleanerListener;
import org.alfresco.repo.domain.contentdata.ContentUrlEntity;
import org.alfresco.repo.transaction.TransactionalResourceHelper;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.util.Pair;
import org.alfresco.util.transaction.TransactionListenerAdapter;
import org.alfresco.util.transaction.TransactionSupportUtil;

/**
 * @author Axel Faust
 */
public class ContentUrlCacheCleaner extends TransactionListenerAdapter implements ContentStoreCleanerListener
{

    // copied from AbstractContentDataDAOImpl
    private static final String CACHE_REGION_CONTENT_URL = "ContentUrl";

    // copied from EagerContentStoreCleaner
    private static final String KEY_POST_COMMIT_DELETION_URLS = "ContentStoreCleaner.PostCommitDeletionUrls";

    // copied from TransactionSupportUtil
    private static final String RESOURCE_KEY_TXN_ID = "AlfrescoTransactionSupport.txnId";

    private static final String KEY_BOUND = ContentUrlCacheCleaner.class.getName() + "-bound";

    // cannot re-use standard callback DAO due to visibility, so this is our own (simplified) variant
    private final ContentUrlCallbackDAO contentUrlCallbackDAO = new ContentUrlCallbackDAO();

    private EntityLookupCache<Long, ContentUrlEntity, String> contentUrlCache;

    /**
     * Sets the list of listeners for the content store cleaner to which this instance should add itself.
     *
     * @param listeners
     *     the list of listeners
     */
    public void setListeners(final List<ContentStoreCleanerListener> listeners)
    {
        listeners.add(this);
    }

    public void setContentUrlCache(final SimpleCache<Long, ContentUrlEntity> contentUrlCache)
    {
        this.contentUrlCache = new EntityLookupCache<>(contentUrlCache, CACHE_REGION_CONTENT_URL, this.contentUrlCallbackDAO);
    }

    public void ensureListenerIsBound()
    {
        final Boolean bound = TransactionSupportUtil.getResource(KEY_BOUND);
        if (!Boolean.TRUE.equals(bound))
        {
            TransactionSupportUtil.bindListener(this, 0);
            TransactionSupportUtil.bindResource(KEY_BOUND, Boolean.TRUE);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeDelete(final ContentStore sourceStore, final String contentUrl) throws ContentIOException
    {
        // unfortunately, listeners are only called during regular orphaned content cleanup batch
        // for txn commit we have to act as a transaction listener too
        // make sure to remove/invalidate the cache entry when content is deleted
        final ContentUrlEntity value = new ContentUrlEntity();
        value.setContentUrl(contentUrl);

        this.withCorrectTxnContext(() -> this.contentUrlCache.removeByValue(value));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterCommit()
    {
        final Set<String> urlsToDelete = TransactionalResourceHelper.getSet(KEY_POST_COMMIT_DELETION_URLS);
        final ContentUrlEntity value = new ContentUrlEntity();

        this.withCorrectTxnContext(() -> {
            for (final String contentUrl : urlsToDelete)
            {
                value.setContentUrl(contentUrl);
                this.contentUrlCache.removeByValue(value);
            }
        });
    }

    private void withCorrectTxnContext(final Runnable run)
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
