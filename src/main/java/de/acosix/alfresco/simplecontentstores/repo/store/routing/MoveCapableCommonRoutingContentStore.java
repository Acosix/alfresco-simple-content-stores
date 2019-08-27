/*
 * Copyright 2017 - 2019 Acosix GmbH
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
package de.acosix.alfresco.simplecontentstores.repo.store.routing;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import java.util.stream.Collectors;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.cache.SimpleCache;
import org.alfresco.repo.content.AbstractRoutingContentStore;
import org.alfresco.repo.content.ContentContext;
import org.alfresco.repo.content.ContentExistsException;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.content.EmptyContentReader;
import org.alfresco.repo.content.NodeContentContext;
import org.alfresco.repo.content.UnsupportedContentUrlException;
import org.alfresco.repo.content.filestore.FileContentStore;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.repo.transaction.TransactionalResourceHelper;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.EqualsHelper;
import org.alfresco.util.GUID;
import org.alfresco.util.Pair;
import org.alfresco.util.PropertyCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import de.acosix.alfresco.simplecontentstores.repo.store.ContentUrlUtils;
import de.acosix.alfresco.simplecontentstores.repo.store.StoreConstants;
import de.acosix.alfresco.simplecontentstores.repo.store.context.ContentStoreContext;
import de.acosix.alfresco.simplecontentstores.repo.store.context.ContentStoreContextInitializer;

/**
 * Instances of this class provide a content store that may support moving of binary contents between different backing stores upon changes
 * to the node referencing the content, e.g. property changes or path relocations.
 *
 * This class partially duplicates code from {@link AbstractRoutingContentStore} since the class hides some relevant internals behind
 * limited visibility modifiers.
 *
 * @author Axel Faust
 */
public abstract class MoveCapableCommonRoutingContentStore<CD> implements ContentStore, ApplicationContextAware, InitializingBean
{

    private static final Logger LOGGER = LoggerFactory.getLogger(MoveCapableCommonRoutingContentStore.class);

    private static final int PROTOCOL_DELIMETER_LENGTH = PROTOCOL_DELIMITER.length();

    private final Object contentStoreContextInitializersLock = new Object();

    protected final String instanceKey = GUID.generate();

    protected ApplicationContext applicationContext;

    protected transient volatile Collection<ContentStoreContextInitializer> contentStoreContextInitializers;

    protected PolicyComponent policyComponent;

    protected DictionaryService dictionaryService;

    protected NodeService nodeService;

    protected SimpleCache<Pair<String, String>, ContentStore> storesByContentUrl;

    protected final ReadLock storesCacheReadLock;

    protected final WriteLock storesCacheWriteLock;

    protected ContentStore fallbackStore;

    protected transient List<ContentStore> allStores;

    public MoveCapableCommonRoutingContentStore()
    {
        super();

        final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
        this.storesCacheReadLock = lock.readLock();
        this.storesCacheWriteLock = lock.writeLock();
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "applicationContext", this.applicationContext);

        PropertyCheck.mandatory(this, "policyComponent", this.policyComponent);
        PropertyCheck.mandatory(this, "dictionaryService", this.dictionaryService);
        PropertyCheck.mandatory(this, "nodeService", this.nodeService);

        PropertyCheck.mandatory(this, "storesByContentUrl", this.storesByContentUrl);
        PropertyCheck.mandatory(this, "fallbackStore", this.fallbackStore);

        if (this.allStores == null)
        {
            this.allStores = new ArrayList<>();
        }

        if (!this.allStores.contains(this.fallbackStore))
        {
            this.allStores.add(this.fallbackStore);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException
    {
        this.applicationContext = applicationContext;
    }

    /**
     * @param policyComponent
     *            the policyComponent to set
     */
    public void setPolicyComponent(final PolicyComponent policyComponent)
    {
        this.policyComponent = policyComponent;
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
     * @param nodeService
     *            the nodeService to set
     */
    public void setNodeService(final NodeService nodeService)
    {
        this.nodeService = nodeService;
    }

    /**
     * Sets the store cache for avoiding repeated content store lookups.
     *
     * @param storesCache
     *            cache of stores used to access URLs
     */
    public void setStoresCache(final SimpleCache<Pair<String, String>, ContentStore> storesCache)
    {
        this.storesByContentUrl = storesCache;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public boolean isContentUrlSupported(final String contentUrl)
    {
        final List<ContentStore> stores = this.getAllStores();
        boolean supported = false;
        for (final ContentStore store : stores)
        {
            if (store.isContentUrlSupported(contentUrl))
            {
                supported = true;
                break;
            }
        }

        LOGGER.debug("The url {} {} supported by at least one store", contentUrl, (supported ? "is" : "is not"));

        return supported;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public boolean isWriteSupported()
    {
        final List<ContentStore> stores = this.getAllStores();
        boolean supported = false;
        for (final ContentStore store : stores)
        {
            if (store.isWriteSupported())
            {
                supported = true;
                break;
            }
        }

        LOGGER.debug("Writing {} supported by at least one store", (supported ? "is" : "is not"));
        return supported;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public String getRootLocation()
    {
        return ".";
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public long getSpaceFree()
    {
        return -1L;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public long getSpaceTotal()
    {
        return -1L;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean exists(final String contentUrl) throws ContentIOException
    {
        boolean exists;
        if (this.isContentUrlSupported(contentUrl))
        {
            final ContentStore store = this.selectReadStore(contentUrl);
            exists = store != null;
        }
        else
        {
            exists = false;
        }
        return exists;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentReader getReader(final String contentUrl) throws ContentIOException
    {
        final ContentReader reader;
        if (this.isContentUrlSupported(contentUrl))
        {
            final ContentStore store = this.selectReadStore(contentUrl);
            if (store != null)
            {
                LOGGER.debug("Getting reader from store: \n\tContent URL: {}\n\tStore: {}", contentUrl, store);
                reader = store.getReader(contentUrl);
            }
            else
            {
                LOGGER.debug("Getting empty reader for content URL: {}", contentUrl);
                reader = new EmptyContentReader(contentUrl);
            }
        }
        else
        {
            LOGGER.debug("Getting empty reader for unsupported content URL: {}", contentUrl);
            reader = new EmptyContentReader(contentUrl);
        }

        return reader;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public ContentWriter getWriter(final ContentContext context) throws ContentIOException
    {
        final String contentUrl = context.getContentUrl();
        final Pair<String, String> cacheKey = new Pair<>(this.instanceKey, contentUrl);
        if (contentUrl != null)
        {
            this.storesCacheReadLock.lock();
            try
            {
                final ContentStore store = this.storesByContentUrl.get(cacheKey);
                if (store != null)
                {
                    throw new ContentExistsException(this, contentUrl);
                }
            }
            finally
            {
                this.storesCacheReadLock.unlock();
            }
        }

        final ContentStore store = this.selectWriteStore(context);

        if (store == null)
        {
            // default Alfresco throws a NullPointerException, which is really inprecise
            throw new IllegalStateException("Unable to find a writer. 'selectWriteStore' may not return null: \n\tRouter: " + this);
        }
        else if (!store.isWriteSupported())
        {
            throw new AlfrescoRuntimeException(
                    "A write store was chosen that doesn't support writes: \n\tRouter: " + this + "\n\tChose:  " + store);
        }
        final ContentWriter writer = store.getWriter(context);
        final String newContentUrl = writer.getContentUrl();
        final Pair<String, String> newCacheKey = new Pair<>(this.instanceKey, newContentUrl);
        // Cache the store against the URL
        this.storesCacheWriteLock.lock();
        try
        {
            this.storesByContentUrl.put(newCacheKey, store);
        }
        finally
        {
            this.storesCacheWriteLock.unlock();
        }

        LOGGER.debug("Got writer and cache URL from store: \n\tContext: {}\n\tWriter:  {}\n\tStore:   {}", context, writer, store);
        return writer;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("deprecation")
    public void getUrls(final ContentUrlHandler handler) throws ContentIOException
    {
        this.getUrls(null, null, handler);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("deprecation")
    public void getUrls(final Date createdAfter, final Date createdBefore, final ContentUrlHandler handler) throws ContentIOException
    {
        final List<ContentStore> stores = this.getAllStores();
        for (final ContentStore store : stores)
        {
            try
            {
                store.getUrls(createdAfter, createdBefore, handler);
            }
            catch (final UnsupportedOperationException e)
            {
                // Support of this is not mandatory
            }
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public boolean delete(final String contentUrl) throws ContentIOException
    {
        boolean deleted = true;
        final List<ContentStore> stores = this.getAllStores();

        /*
         * This operation has to be performed on all the stores in order to maintain the
         * {@link ContentStore#exists(String)} contract.
         * Still need to apply the isContentUrlSupported guard though.
         */
        for (final ContentStore store : stores)
        {
            if (store.isContentUrlSupported(contentUrl) && store.isWriteSupported())
            {
                deleted &= store.delete(contentUrl);
            }
        }

        LOGGER.debug("Deleted content URL from stores: \n\tStores:  {}\n\tDeleted: {}", stores.size(), deleted);

        return deleted;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append(this.getClass().getSimpleName()).append(" [instanceKey=").append(this.instanceKey).append("]");
        return builder.toString();
    }

    /**
     * Checks the cache for the store and ensures that the URL is in the store.
     *
     * @param contentUrl
     *            the content URL to search for
     * @return the store matching the content URL
     */
    protected ContentStore selectReadStore(final String contentUrl)
    {
        final ContentStore readStore = this.getStore(contentUrl, true);
        return readStore;
    }

    /**
     *
     * Retrieves all the stores backing this instance.
     *
     * @return the list of all possible stores available for reading or writing
     */
    protected List<ContentStore> getAllStores()
    {
        return Collections.unmodifiableList(this.allStores);
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
        final List<ContentStore> allStores = this.getAllStores();
        final List<ContentStore> filteredStores = allStores.stream().filter(store -> store.isContentUrlSupported(contentUrl))
                .collect(Collectors.toList());
        return filteredStores;
    }

    protected ContentStore getStore(final String contentUrl, final boolean mustExist)
    {
        ContentStore store = this.getStoreFromCache(contentUrl, mustExist);
        if (store == null || !store.exists(contentUrl))
        {
            this.storesCacheWriteLock.lock();
            try
            {
                store = this.getStoreFromCache(contentUrl, mustExist);
                if (store == null)
                {
                    store = this.selectStore(contentUrl, mustExist);

                    if (store != null)
                    {
                        final Pair<String, String> cacheKey = new Pair<>(this.instanceKey, contentUrl);
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

        final List<ContentStore> stores = this.getStores(contentUrl);

        if (stores.isEmpty())
        {
            throw new UnsupportedContentUrlException(this, contentUrl);
        }

        for (final ContentStore storeInList : stores)
        {
            if (!mustExist || storeInList.exists(contentUrl))
            {
                store = storeInList;
                break;
            }
        }

        return store;
    }

    protected ContentStore getStoreFromCache(final String contentUrl, final boolean mustExist)
    {
        ContentStore readStore = null;
        final Pair<String, String> cacheKey = new Pair<>(this.instanceKey, contentUrl);
        this.storesCacheReadLock.lock();
        try
        {
            final ContentStore store = this.storesByContentUrl.get(cacheKey);
            if (store != null)
            {
                try
                {
                    if (!mustExist || store.exists(contentUrl))
                    {
                        readStore = store;
                    }
                }
                catch (final UnsupportedContentUrlException e)
                {
                    // This is odd. The store that previously supported the content URL
                    // no longer does so. I can't think of a reason why that would be.
                    throw new AlfrescoRuntimeException("Found a content store that previously supported a URL, but no longer does: \n"
                            + "\tStore: " + store + "\n" + "\tContent URL: " + contentUrl);
                }
            }
        }
        finally
        {
            this.storesCacheReadLock.unlock();
        }
        return readStore;
    }

    /**
     * Gets a content store based on the context provided. The applicability of the
     * context and even the types of context allowed are up to the implementation, but
     * normally there should be a fallback case for when the parameters are not adequate
     * to make a decision.
     *
     * @param ctx
     *            the context to use to make the choice
     * @return the store most appropriate for the given context and
     *         <b>never <tt>null</tt></b>
     */
    protected ContentStore selectWriteStore(final ContentContext ctx)
    {
        final ContentStore writeStore;

        if (this.isRoutable(ctx))
        {
            writeStore = this.selectWriteStoreFromRoutes(ctx);
        }
        else
        {
            writeStore = this.fallbackStore;
        }

        return writeStore;
    }

    protected ContentStore selectWriteStoreFromRoutes(final ContentContext ctx)
    {
        final String contentUrl = ctx.getContentUrl();

        final ContentStore writeStore;
        if (contentUrl != null)
        {
            LOGGER.debug("Selecting store based on provided content URL to write {}", ctx);
            writeStore = this.getStore(contentUrl, false);
        }
        else
        {
            LOGGER.debug("Selecting fallback store to write {}", ctx);
            writeStore = this.fallbackStore;
        }

        return writeStore;
    }

    protected boolean isRoutable(final ContentContext ctx)
    {
        return true;
    }

    protected ContentStore selectStoreForContentDataMove(final NodeRef nodeRef, final QName propertyQName, final ContentData contentData,
            final CD customData)
    {
        return this.fallbackStore;
    }

    protected void checkAndProcessContentPropertiesMove(final NodeRef affectedNode, final boolean defaultMoveFlag,
            final QName moveFlagOverridePropertyQName, final CD customData)
    {
        // just copied/moved so properties should be cached
        final Map<QName, Serializable> properties = this.nodeService.getProperties(affectedNode);

        boolean doMove = false;
        if (moveFlagOverridePropertyQName != null)
        {
            final Serializable moveStoresOnChangeOptionValue = properties.get(moveFlagOverridePropertyQName);
            // explicit value wins
            if (moveStoresOnChangeOptionValue != null)
            {
                LOGGER.debug("Using override property value {} to determine doMove state", moveStoresOnChangeOptionValue);
                doMove = Boolean.TRUE.equals(moveStoresOnChangeOptionValue);
            }
            else
            {
                doMove = defaultMoveFlag;
            }
        }
        else
        {
            doMove = defaultMoveFlag;
        }
        LOGGER.debug("Determined doMove flag state of {} for {}", doMove, affectedNode);

        if (doMove)
        {
            this.checkAndProcessContentPropertiesMove(affectedNode, properties, customData);
        }
    }

    protected void checkAndProcessContentPropertiesMove(final NodeRef affectedNode, final Map<QName, Serializable> properties,
            final CD customData)
    {
        final Collection<QName> contentProperties = this.dictionaryService.getAllProperties(DataTypeDefinition.CONTENT);

        final Collection<QName> setProperties = new HashSet<>(properties.keySet());
        setProperties.retainAll(contentProperties);

        LOGGER.debug("Found {} set content properties on {}", setProperties.size(), affectedNode);

        if (!setProperties.isEmpty())
        {
            final Map<QName, Serializable> contentPropertiesMap = new HashMap<>();
            for (final QName contentProperty : setProperties)
            {
                final Serializable value = properties.get(contentProperty);
                if (value != null)
                {
                    contentPropertiesMap.put(contentProperty, value);
                }
            }

            if (!contentPropertiesMap.isEmpty())
            {
                LOGGER.debug("Processing {} set content properties with non-null values on {}", contentPropertiesMap.size(), affectedNode);

                ContentStoreContext.executeInNewContext(() -> {
                    MoveCapableCommonRoutingContentStore.this.processContentPropertiesMove(affectedNode, contentPropertiesMap,
                            customData);
                    return null;
                });
            }
        }
    }

    protected void processContentPropertiesMove(final NodeRef nodeRef, final Map<QName, Serializable> contentProperties,
            final CD customData)
    {
        final Map<QName, Serializable> updates = new HashMap<>();
        for (final Entry<QName, Serializable> contentPropertyEntry : contentProperties.entrySet())
        {
            final QName contentProperty = contentPropertyEntry.getKey();
            this.processContentPropertyMove(nodeRef, contentProperty, contentPropertyEntry.getValue(), updates, customData);
        }

        if (!updates.isEmpty())
        {
            this.nodeService.addProperties(nodeRef, updates);
        }
    }

    protected void processContentPropertyMove(final NodeRef nodeRef, final QName propertyQName, final Serializable value,
            final Map<QName, Serializable> updates, final CD customData)
    {
        if (value instanceof ContentData)
        {
            final ContentData contentData = (ContentData) value;
            final ContentData updatedContentData = this.processContentDataMove(nodeRef, propertyQName, contentData, customData);
            if (updatedContentData != null)
            {
                updates.put(propertyQName, updatedContentData);
            }
        }
        else if (value instanceof Collection<?>)
        {
            final Collection<?> values = (Collection<?>) value;
            final List<Object> updatedValues = new ArrayList<>();
            for (final Object valueElement : values)
            {
                if (valueElement instanceof ContentData)
                {
                    final ContentData updatedContentData = this.processContentDataMove(nodeRef, propertyQName, (ContentData) valueElement,
                            customData);
                    if (updatedContentData != null)
                    {
                        updatedValues.add(updatedContentData);
                    }
                    else
                    {
                        updatedValues.add(valueElement);
                    }
                }
                else
                {
                    updatedValues.add(valueElement);
                }
            }

            if (!EqualsHelper.nullSafeEquals(values, updatedValues))
            {
                updates.put(propertyQName, (Serializable) updatedValues);
            }
        }
    }

    protected ContentData processContentDataMove(final NodeRef nodeRef, final QName propertyQName, final ContentData contentData,
            final CD customData)
    {
        LOGGER.debug("Processing content data {} for property {} on node {}", contentData, propertyQName, nodeRef);

        ContentData updatedContentData = null;
        boolean noContentDataUpdateRequired = false;
        final String currentContentUrl = contentData.getContentUrl();

        LOGGER.debug("Checking if content URL {} exists in store {}", currentContentUrl, this);
        if (this.exists(currentContentUrl))
        {
            LOGGER.debug("Handling existing content for URL {}", currentContentUrl);

            this.initializeContentStoreContext(nodeRef, propertyQName);

            final ContentStore targetStore = this.selectStoreForContentDataMove(nodeRef, propertyQName, contentData, customData);

            final Pair<String, String> urlParts = this.getContentUrlParts(currentContentUrl);
            final String protocol = urlParts.getFirst();
            final String baseContentUrl = ContentUrlUtils.getBaseContentUrl(currentContentUrl);

            // use wildcard and legacy store protocols to check for existing content
            // if the current content URL / existing content is still valid for the potentially different context after move, one of the
            // wildcard URLs should resolve back to it
            final String oldWildcardContentUrl = StoreConstants.WILDCARD_PROTOCOL + currentContentUrl.substring(protocol.length());
            final String oldWildcardBaseContentUrl = StoreConstants.WILDCARD_PROTOCOL + baseContentUrl.substring(protocol.length());
            final String legacyContentUrl = FileContentStore.STORE_PROTOCOL + baseContentUrl.substring(protocol.length());

            final Set<String> urlsToTest = new LinkedHashSet<>(
                    Arrays.asList(oldWildcardContentUrl, oldWildcardBaseContentUrl, legacyContentUrl));

            LOGGER.debug("Checking if target store {} already contains content for base content URL {}", targetStore, baseContentUrl);
            String firstSupportedContentUrl = null;
            for (final String urlToTest : urlsToTest)
            {
                if (targetStore.isContentUrlSupported(urlToTest))
                {
                    if (firstSupportedContentUrl == null)
                    {
                        firstSupportedContentUrl = urlToTest;
                    }

                    final ContentReader reader = targetStore.getReader(urlToTest);
                    if (reader != null && reader.exists())
                    {
                        final String targetStoreContentUrl = reader.getContentUrl();
                        if (!EqualsHelper.nullSafeEquals(currentContentUrl, targetStoreContentUrl))
                        {
                            LOGGER.debug("Content already exists in target store with identified by content URL {} - updating content data",
                                    targetStoreContentUrl);

                            reader.setMimetype(contentData.getMimetype());
                            reader.setEncoding(contentData.getEncoding());
                            reader.setLocale(contentData.getLocale());

                            updatedContentData = reader.getContentData();
                            break;
                        }

                        LOGGER.debug(
                                "Content already exists in target store with identical content URL - no need to copy content / update content data");
                        updatedContentData = null;
                        noContentDataUpdateRequired = true;
                        break;
                    }
                }
            }

            if (updatedContentData == null && !noContentDataUpdateRequired)
            {
                final ContentReader reader = this.getReader(currentContentUrl);
                if (reader == null || !reader.exists())
                {
                    throw new AlfrescoRuntimeException("Can't copy content since original content does not exist");
                }

                final NodeContentContext contentContext = new NodeContentContext(reader, firstSupportedContentUrl, nodeRef, propertyQName);
                final ContentWriter writer = targetStore.getWriter(contentContext);

                final String newContentUrl = writer.getContentUrl();

                LOGGER.debug("Copying content of {} on {} from {} to {}", propertyQName, nodeRef, currentContentUrl, newContentUrl);

                // ensure content cleanup on rollback (only if a new, unique URL was created
                if (!EqualsHelper.nullSafeEquals(currentContentUrl, newContentUrl))
                {
                    final Set<String> urlsToDelete = TransactionalResourceHelper.getSet(StoreConstants.KEY_POST_ROLLBACK_DELETION_URLS);
                    urlsToDelete.add(newContentUrl);
                }

                writer.putContent(reader);

                // copy manually to keep original values (writing into different writer may change, e.g. size, due to transparent
                // transformations, i.e. compression)
                updatedContentData = new ContentData(writer.getContentUrl(), contentData.getMimetype(), contentData.getSize(),
                        contentData.getEncoding(), contentData.getLocale());
            }
        }
        else
        {
            LOGGER.debug("Not handling {} on {} as content URL does not exist in this store", contentData, nodeRef);
        }

        return updatedContentData;
    }

    protected void ensureInitializersAreSet()
    {
        if (this.contentStoreContextInitializers == null)
        {
            synchronized (this.contentStoreContextInitializersLock)
            {
                if (this.contentStoreContextInitializers == null)
                {
                    this.contentStoreContextInitializers = this.applicationContext
                            .getBeansOfType(ContentStoreContextInitializer.class, false, false).values();
                }
            }
        }
    }

    protected void initializeContentStoreContext(final NodeRef nodeRef, final QName propertyQName)
    {
        this.ensureInitializersAreSet();

        for (final ContentStoreContextInitializer initializer : this.contentStoreContextInitializers)
        {
            initializer.initialize(nodeRef, propertyQName);
        }
    }

    // copied from AbstractContentStore
    /**
     * Splits the content URL into its component parts as separated by {@link ContentStore#PROTOCOL_DELIMITER protocol delimiter}.
     *
     * @param contentUrl
     *            the content URL to split
     * @return the protocol and identifier portions of the content URL, both of which will not be <tt>null</tt>
     * @throws UnsupportedContentUrlException
     *             if the content URL is invalid
     */
    protected Pair<String, String> getContentUrlParts(final String contentUrl)
    {
        if (contentUrl == null)
        {
            throw new IllegalArgumentException("The contentUrl may not be null");
        }
        final int index = contentUrl.indexOf(ContentStore.PROTOCOL_DELIMITER);
        if (index <= 0)
        {
            throw new UnsupportedContentUrlException(this, contentUrl);
        }
        final String protocol = contentUrl.substring(0, index);
        final String identifier = contentUrl.substring(index + PROTOCOL_DELIMETER_LENGTH, contentUrl.length());
        if (identifier.length() == 0)
        {
            throw new UnsupportedContentUrlException(this, contentUrl);
        }
        return new Pair<>(protocol, identifier);
    }
}
