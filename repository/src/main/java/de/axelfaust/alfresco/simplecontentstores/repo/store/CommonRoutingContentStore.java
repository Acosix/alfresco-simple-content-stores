/*
 * Copyright 2016 Axel Faust
 *
 * Licensed under the Eclipse Public License (EPL), Version 1.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package de.axelfaust.alfresco.simplecontentstores.repo.store;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.cache.SimpleCache;
import org.alfresco.repo.content.AbstractRoutingContentStore;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.content.EmptyContentReader;
import org.alfresco.repo.content.UnsupportedContentUrlException;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.dictionary.PropertyDefinition;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.alfresco.util.ParameterCheck;
import org.alfresco.util.PropertyCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

/**
 * @author Axel Faust
 */
public abstract class CommonRoutingContentStore extends AbstractRoutingContentStore implements InitializingBean
{

    private static final Logger LOGGER = LoggerFactory.getLogger(CommonRoutingContentStore.class);

    protected NamespaceService namespaceService;

    protected DictionaryService dictionaryService;

    protected List<String> routeContentPropertyNames;

    protected transient Set<QName> routeContentPropertyQNames;

    protected ContentStore fallbackStore;

    protected final String instanceKey;

    protected SimpleCache<Pair<String, String>, ContentStore> storesByContentUrl;

    protected final ReadLock storesCacheReadLock;

    protected final WriteLock storesCacheWriteLock;

    public CommonRoutingContentStore()
    {
        super();

        // we may need those locks and the instance key but base class does not allow simple access to them
        try
        {
            final Field readLockField = AbstractRoutingContentStore.class.getDeclaredField("storesCacheReadLock");
            readLockField.setAccessible(true);
            this.storesCacheReadLock = (ReadLock) readLockField.get(this);

            final Field writeLockField = AbstractRoutingContentStore.class.getDeclaredField("storesCacheWriteLock");
            writeLockField.setAccessible(true);
            this.storesCacheWriteLock = (WriteLock) writeLockField.get(this);

            final Field instanceKeyField = AbstractRoutingContentStore.class.getDeclaredField("instanceKey");
            instanceKeyField.setAccessible(true);
            this.instanceKey = (String) instanceKeyField.get(this);
        }
        catch (final NoSuchFieldException nsfe)
        {
            throw new AlfrescoRuntimeException("Can't access required fields from base class", nsfe);
        }
        catch (final IllegalAccessException iae)
        {
            throw new AlfrescoRuntimeException("Can't access required fields from base class", iae);
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "namespaceService", this.namespaceService);
        PropertyCheck.mandatory(this, "dictionaryService", this.dictionaryService);

        PropertyCheck.mandatory(this, "fallbackStore", this.fallbackStore);

        PropertyCheck.mandatory(this, "storesByContentUrl", this.storesByContentUrl);

        this.afterPropertiesSet_setupRouteContentProperties();
    }

    /**
     * @param namespaceService
     *            the namespaceService to set
     */
    public void setNamespaceService(final NamespaceService namespaceService)
    {
        this.namespaceService = namespaceService;
    }

    /**
     * @param dictionaryService
     *            the dictionaryService to set
     */
    public void setDictionaryService(final DictionaryService dictionaryService)
    {
        this.dictionaryService = dictionaryService;
    }

    /**
     * @param routeContentPropertyNames
     *            the routeContentPropertyNames to set
     */
    public void setRouteContentPropertyNames(final List<String> routeContentPropertyNames)
    {
        this.routeContentPropertyNames = routeContentPropertyNames;
    }

    /**
     * @param fallbackStore
     *            the fallbackStore to set
     */
    public void setFallbackStore(final ContentStore fallbackStore)
    {
        this.fallbackStore = fallbackStore;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void setStoresCache(final SimpleCache<Pair<String, String>, ContentStore> storesCache)
    {
        super.setStoresCache(storesCache);
        this.storesByContentUrl = storesCache;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean exists(final String contentUrl) throws ContentIOException
    {
        final ContentStore store = this.selectReadStore(contentUrl);
        return (store != null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentReader getReader(final String contentUrl) throws ContentIOException
    {
        final ContentStore store = this.selectReadStore(contentUrl);
        final ContentReader reader;
        if (store != null)
        {
            LOGGER.debug("Getting reader from store: \n   Content URL: {0}\n   Store:       {1}", contentUrl, store);
            reader = store.getReader(contentUrl);
        }
        else
        {
            LOGGER.debug("Getting empty reader for content URL: {0}", contentUrl);
            reader = new EmptyContentReader(contentUrl);
        }

        return reader;
    }

    // needed to copy this private-visibility method from AbstractRoutingContentStore for improved granularity
    protected ContentStore selectReadStore(final String contentUrl)
    {
        final ContentStore readStore = this.getStore(contentUrl, true);
        return readStore;
    }

    protected ContentStore getStore(final String contentUrl, final boolean mustExist)
    {
        ContentStore store = this.getStoreFromCache(contentUrl, mustExist);
        if (store == null || !store.exists(contentUrl))
        {
            // Get the write lock and double check
            this.storesCacheWriteLock.lock();
            try
            {
                // Double check
                store = this.getStoreFromCache(contentUrl, mustExist);
                if (store == null)
                {
                    store = this.selectStore(contentUrl, mustExist);

                    if (store != null)
                    {
                        // Put the value in the cache
                        final Pair<String, String> cacheKey = new Pair<String, String>(this.instanceKey, contentUrl);
                        this.storesByContentUrl.put(cacheKey, store);
                    }
                }
            }
            finally
            {
                this.storesCacheWriteLock.unlock();
            }
        }

        return store;
    }

    protected ContentStore selectStore(final String contentUrl, final boolean mustExist)
    {
        ContentStore store = null;

        // Keep track of the unsupported state of the content URL - it might be a rubbish URL
        boolean contentUrlSupported = false;

        // first step - optimized store traversal over potential sub-list of pre-filtered stores
        final List<ContentStore> stores = this.getStores(contentUrl);
        for (final ContentStore storeInList : stores)
        {
            if (storeInList.isContentUrlSupported(contentUrl))
            {
                // At least the content URL was supported
                contentUrlSupported = true;

                if (!mustExist || storeInList.exists(contentUrl))
                {
                    store = storeInList;
                    break;
                }
            }
        }

        if (store == null)
        {
            final List<ContentStore> allStores = this.getAllStores();
            if (allStores != stores)
            {
                for (final ContentStore storeInList : allStores)
                {
                    if (storeInList.isContentUrlSupported(contentUrl))
                    {
                        // At least the content URL was supported
                        contentUrlSupported = true;

                        if (!mustExist || storeInList.exists(contentUrl))
                        {
                            store = storeInList;
                            break;
                        }
                    }
                }
            }
        }

        // Check if the content URL was supported
        if (!contentUrlSupported)
        {
            throw new UnsupportedContentUrlException(this, contentUrl);
        }

        return store;
    }

    /**
     * Retrieves the (sub-)list of stores that may be relevant to handle a specific content URL
     *
     * @param contentUrl
     *            the content URL to handle
     * @return the (sub-)liust of stores
     */
    protected List<ContentStore> getStores(final String contentUrl)
    {
        return this.getAllStores();
    }

    protected ContentStore getStoreFromCache(final String contentUrl, final boolean mustExist)
    {
        ContentStore readStore = null;
        final Pair<String, String> cacheKey = new Pair<String, String>(this.instanceKey, contentUrl);
        this.storesCacheReadLock.lock();
        try
        {
            // Check if the store is in the cache
            final ContentStore store = this.storesByContentUrl.get(cacheKey);
            if (store != null)
            {
                // We found a store that was previously used
                try
                {
                    // It is possible for content to be removed from a store and
                    // it might have moved into another store.
                    if (!mustExist || store.exists(contentUrl))
                    {
                        // We found a store and can use it
                        readStore = store;
                    }
                }
                catch (final UnsupportedContentUrlException e)
                {
                    // This is odd. The store that previously supported the content URL
                    // no longer does so. I can't think of a reason why that would be.
                    throw new AlfrescoRuntimeException("Found a content store that previously supported a URL, but no longer does: \n"
                            + "   Store:       " + store + "\n" + "   Content URL: " + contentUrl);
                }
            }
        }
        finally
        {
            this.storesCacheReadLock.unlock();
        }
        return readStore;
    }

    private void afterPropertiesSet_setupRouteContentProperties()
    {
        if (this.routeContentPropertyNames != null && !this.routeContentPropertyNames.isEmpty())
        {
            this.routeContentPropertyQNames = new HashSet<QName>();
            for (final String routePropertyName : this.routeContentPropertyNames)
            {
                final QName routePropertyQName = QName.resolveToQName(this.namespaceService, routePropertyName);
                ParameterCheck.mandatory("routePropertyQName", routePropertyQName);

                final PropertyDefinition contentPropertyDefinition = this.dictionaryService.getProperty(routePropertyQName);
                if (contentPropertyDefinition == null
                        || !DataTypeDefinition.CONTENT.equals(contentPropertyDefinition.getDataType().getName()))
                {
                    throw new IllegalStateException(routePropertyName + " is not a valid content model property of type d:content");
                }
                this.routeContentPropertyQNames.add(routePropertyQName);
            }
        }
    }
}
