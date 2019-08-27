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
package de.acosix.alfresco.simplecontentstores.repo.store.combination;

import java.util.ArrayList;
import java.util.List;

import org.alfresco.repo.content.AbstractContentStore;
import org.alfresco.repo.content.ContentContext;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.util.PropertyCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

/**
 * This content store implementation is closely based on the default Alfresco
 * {@link org.alfresco.repo.content.replication.AggregatingContentStore}, adding the following improvements:
 * <ul>
 * <li>configurable deletion of content from secondary stores (enabled by default)</li>
 * <li>removal of pointless read-locking which (in the original class) is a remnant of the legacy ReplicatingContentStore implementation
 * that was the precursor to the aggregating content store implementation</li>
 * <li>checking {@link ContentStore#isContentUrlSupported(String) support of a specific content URL} is done across both primary and
 * secondary stores</li>
 * </ul>
 *
 * Just like the default Alfresco implementation, content is only written to the primary store.
 *
 * If the {@link #setPrimaryStore(ContentStore) primary content store} on an instance of this class does not
 * {@link ContentStore#isWriteSupported() support write operations} (which is used to determine if {@link #isWriteSupported() write is
 * supported} in this implementation) then the default Alfresco code to call {@link ContentStore#delete(String) delete} will not call the
 * respective instance of this implementation, and as a result content will not be deleted from any store (primary or secondary).
 *
 * Due to the unique nature of content URLs, the {@link #setDeleteContentFromSecondaryStores(boolean) deletion of content from secondary
 * stores} will only work if the secondary stores support of content URL patterns and protocols overlaps for the specific content URL to be
 * deleted.
 *
 * @author Axel Faust
 */
public class AggregatingContentStore extends AbstractContentStore implements InitializingBean
{

    private static final Logger LOGGER = LoggerFactory.getLogger(AggregatingContentStore.class);

    protected ContentStore primaryStore;

    protected List<ContentStore> secondaryStores;

    protected boolean deleteContentFromSecondaryStores = true;

    protected transient List<ContentStore> allStores;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "primaryStore", this.primaryStore);
        PropertyCheck.mandatory(this, "secondaryStores", this.secondaryStores);

        this.allStores = new ArrayList<>(1 + this.secondaryStores.size());
        this.allStores.add(this.primaryStore);
        this.allStores.addAll(this.secondaryStores);
    }

    /**
     * @param primaryStore
     *            the primaryStore to set
     */
    public void setPrimaryStore(final ContentStore primaryStore)
    {
        this.primaryStore = primaryStore;
    }

    /**
     * @param secondaryStores
     *            the secondaryStores to set
     */
    public void setSecondaryStores(final List<ContentStore> secondaryStores)
    {
        // decouple
        this.secondaryStores = secondaryStores != null ? new ArrayList<>(secondaryStores) : null;
    }

    /**
     * @param deleteContentFromSecondaryStores
     *            the deleteContentFromSecondaryStores to set
     */
    public void setDeleteContentFromSecondaryStores(final boolean deleteContentFromSecondaryStores)
    {
        this.deleteContentFromSecondaryStores = deleteContentFromSecondaryStores;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public boolean isWriteSupported()
    {
        final boolean writeSupported = this.primaryStore.isWriteSupported();
        return writeSupported;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public boolean isContentUrlSupported(final String contentUrl)
    {
        LOGGER.trace("Checking support for content URL {} across all stores", contentUrl);
        boolean supported = false;

        for (final ContentStore store : this.allStores)
        {
            supported = supported || store.isContentUrlSupported(contentUrl);
        }
        LOGGER.trace("Content URL {} is {}supported", supported ? "" : "not ");
        return supported;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public String getRootLocation()
    {
        final String rootLocation = this.primaryStore.getRootLocation();
        return rootLocation;
    }

    @Override
    public ContentReader getReader(final String contentUrl)
    {
        ContentReader reader = null;
        boolean validReader = false;

        LOGGER.debug("Retrieving content reader for URL {}", contentUrl);

        if (this.primaryStore.isContentUrlSupported(contentUrl))
        {
            reader = this.primaryStore.getReader(contentUrl);
            validReader = reader != null && reader.exists();

            if (validReader)
            {
                LOGGER.debug("Content reader for URL {} retrieved from primary store", contentUrl);
            }
        }

        for (int idx = 0, max = this.secondaryStores.size(); idx < max && !validReader; idx++)
        {
            final ContentStore store = this.secondaryStores.get(idx);
            if (store.isContentUrlSupported(contentUrl))
            {
                reader = store.getReader(contentUrl);

                validReader = reader != null && reader.exists();
                if (validReader)
                {
                    LOGGER.debug("Content reader for URL {} retrieved from secondary store #{}", contentUrl, idx + 1);
                }
            }
        }

        if (!validReader)
        {
            LOGGER.debug("No content reader with existing content found for URL {}", contentUrl);
        }

        return reader;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public ContentWriter getWriter(final ContentContext ctx)
    {
        final ContentWriter writer = this.primaryStore.getWriter(ctx);
        return writer;
    }

    @Override
    public boolean delete(final String contentUrl)
    {
        // allMatch on stream might short-circuit and not trigger delete on all stores
        boolean considerDeleted;

        if (this.deleteContentFromSecondaryStores)
        {
            LOGGER.debug("Deleting content URL {} from primary and secondary stores", contentUrl);
            considerDeleted = true;
            for (final ContentStore store : this.allStores)
            {
                boolean deleted = false;
                if (!store.isWriteSupported())
                {
                    deleted = true;
                }
                else if (!store.isContentUrlSupported(contentUrl))
                {
                    deleted = true;
                }
                else
                {
                    deleted = store.delete(contentUrl);
                }
                considerDeleted = deleted && considerDeleted;
            }
            LOGGER.debug("Content URL {} {}successfully deleted from both primary and secondary stores", contentUrl,
                    considerDeleted ? "" : "not ");
        }
        else
        {
            LOGGER.debug("Deleting content URL {} from primary store only", contentUrl);
            if (!this.primaryStore.isWriteSupported())
            {
                considerDeleted = true;
            }
            else if (!this.primaryStore.isContentUrlSupported(contentUrl))
            {
                considerDeleted = true;
            }
            else
            {
                considerDeleted = this.primaryStore.delete(contentUrl);
            }
            LOGGER.debug("Content URL {} {}sucessfully deleted from primary store", contentUrl, considerDeleted ? "" : "not ");
        }

        return considerDeleted;
    }
}
