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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.content.cleanup.ContentStoreCleanerListener;
import org.alfresco.repo.domain.contentdata.ContentUrlEntity;
import org.alfresco.repo.transaction.TransactionalResourceHelper;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.util.Pair;
import org.alfresco.util.PropertyCheck;
import org.alfresco.util.transaction.TransactionListenerAdapter;
import org.alfresco.util.transaction.TransactionSupportUtil;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Instances of this class ensure to maintain consistency with regards to content URLs for the following cases:
 * <ol>
 * <li>content URL is dereferenced and marked as orphaned - invalidate the cache entry</li>
 * <li>content URL is deleted - invalidate the cache entry</li>
 * <li>content URL is un-orphaned - remove the orphan mark and invalidate the cache entry</li>
 * </ol>
 *
 * @author Axel Faust
 */
public class ContentUrlConsistencyHandler extends TransactionListenerAdapter implements ContentStoreCleanerListener, InitializingBean
{

    // copied from AbstractContentDataDAOImpl
    private static final String KEY_PRE_COMMIT_CONTENT_URL_DELETIONS = "AbstractContentDataDAOImpl.PreCommitContentUrlDeletions";

    // copied from EagerContentStoreCleaner
    private static final String KEY_POST_COMMIT_DELETION_URLS = "ContentStoreCleaner.PostCommitDeletionUrls";

    // copied from EagerContentStoreCleaner
    private static final String KEY_POST_ROLLBACK_DELETION_URLS = "ContentStoreCleaner.PostRollbackDeletionUrls";

    private static final String KEY_BOUND = ContentUrlConsistencyHandler.class.getName() + "-bound";

    private static final String KEY_ORPHAN_RECHECK_CONTENT_URLS = ContentUrlConsistencyHandler.class.getName()
            + "-orphanRecheckContentUrls";

    private ContentUrlConsistencyDAO contentUrlConsistencyDAO;

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet() throws Exception
    {
        PropertyCheck.mandatory(this, "contentUrlConsistencyDAO", this.contentUrlConsistencyDAO);
    }

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

    /**
     * @param contentUrlConsistencyDAO
     *     the contentUrlConsistencyDAO to set
     */
    public void setContentUrlConsistencyDAO(final ContentUrlConsistencyDAO contentUrlConsistencyDAO)
    {
        this.contentUrlConsistencyDAO = contentUrlConsistencyDAO;
    }

    /**
     * Ensures that this instance is bound as a listener on the currently active transaction.
     */
    public void ensureListenerIsBound()
    {
        final Boolean bound = TransactionSupportUtil.getResource(KEY_BOUND);
        if (!Boolean.TRUE.equals(bound))
        {
            TransactionSupportUtil.bindListener(this, 0);
            TransactionSupportUtil.bindResource(KEY_BOUND, Boolean.TRUE);
        }
    }

    public void registerOrphanRecheckContentUrl(final String contentUrl)
    {
        TransactionalResourceHelper.getSet(KEY_ORPHAN_RECHECK_CONTENT_URLS).add(contentUrl);
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
        this.contentUrlConsistencyDAO.invalidateCachedContentUrlEntity(contentUrl);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeCommit(final boolean readOnly)
    {
        // ContentUrlDeleteTransactionListener clears the set so we have to act before that
        // this requires ensureListenerIsBound is called as early as possible
        final Set<String> dereferencedContentUrls = TransactionalResourceHelper.getSet(KEY_PRE_COMMIT_CONTENT_URL_DELETIONS);
        if (!dereferencedContentUrls.isEmpty())
        {
            this.contentUrlConsistencyDAO.invalidateCachedContentUrlEntities(dereferencedContentUrls);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeCompletion()
    {
        // Alfresco has no un-orphan handling by default - we should check any content URLs that were registered as "new" if they warrant
        // un-orphaning
        final Set<String> postRollbackDeletionUrls = TransactionalResourceHelper.getSet(KEY_POST_ROLLBACK_DELETION_URLS);
        final Set<String> explicitOrphanRecheckUrls = TransactionalResourceHelper.getSet(KEY_ORPHAN_RECHECK_CONTENT_URLS);
        final Set<String> urlsToCheck = new HashSet<>(postRollbackDeletionUrls);
        urlsToCheck.addAll(explicitOrphanRecheckUrls);

        for (final String contentUrl : urlsToCheck)
        {
            final Pair<ContentUrlEntity, Boolean> urlEntityUnreferenced = this.contentUrlConsistencyDAO
                    .getContentUrlEntityUnreferenced(contentUrl);
            if (urlEntityUnreferenced != null && !Boolean.TRUE.equals(urlEntityUnreferenced.getSecond()))
            {
                final ContentUrlEntity entity = urlEntityUnreferenced.getFirst();
                final Long orphanTime = entity.getOrphanTime();
                if (orphanTime != null)
                {
                    // entity had previously been marked as orphaned but now is referenced
                    if (!this.contentUrlConsistencyDAO.unoprhanContentUrl(entity.getId(), orphanTime))
                    {
                        throw new DataIntegrityViolationException("Failed to unorphan content URL entity " + entity.getId());
                    }
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterCommit()
    {
        final Set<String> urlsToDelete = TransactionalResourceHelper.getSet(KEY_POST_COMMIT_DELETION_URLS);
        final Set<String> explicitOrphanRecheckUrls = TransactionalResourceHelper.getSet(KEY_ORPHAN_RECHECK_CONTENT_URLS);
        final Set<String> urlsToInvalidate = new HashSet<>(urlsToDelete);
        urlsToInvalidate.addAll(explicitOrphanRecheckUrls);

        if (!urlsToInvalidate.isEmpty())
        {
            this.contentUrlConsistencyDAO.invalidateCachedContentUrlEntities(urlsToInvalidate);
        }
    }
}
