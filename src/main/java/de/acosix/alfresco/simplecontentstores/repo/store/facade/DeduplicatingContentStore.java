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
package de.acosix.alfresco.simplecontentstores.repo.store.facade;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.UUID;

import org.alfresco.repo.content.ContentContext;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.util.PropertyCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.acosix.alfresco.simplecontentstores.repo.store.StoreConstants;

/**
 * @author Axel Faust
 */
public class DeduplicatingContentStore extends CommonFacadingContentStore
{

    private static final Logger LOGGER = LoggerFactory.getLogger(DeduplicatingContentStore.class);

    protected ContentStore temporaryStore;

    protected String digestAlgorithm = "SHA-512";

    protected String digestAlgorithmProvider;

    protected int pathSegments = 3;

    protected int bytesPerPathSegment = 2;

    protected transient String dummyUrlPrefix;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        super.afterPropertiesSet();

        PropertyCheck.mandatory(this, "temporaryStore", this.temporaryStore);

        final MessageFormat mf = new MessageFormat("{0}{1}dummy/", Locale.ENGLISH);
        this.dummyUrlPrefix = mf.format(new Object[] { StoreConstants.WILDCARD_PROTOCOL, ContentStore.PROTOCOL_DELIMITER });
    }

    /**
     * @param temporaryStore
     *            the temporaryStore to set
     */
    public void setTemporaryStore(final ContentStore temporaryStore)
    {
        this.temporaryStore = temporaryStore;
    }

    /**
     * @param digestAlgorithm
     *            the digestAlgorithm to set
     */
    public void setDigestAlgorithm(final String digestAlgorithm)
    {
        this.digestAlgorithm = digestAlgorithm;
    }

    /**
     * @param digestAlgorithmProvider
     *            the digestAlgorithmProvider to set
     */
    public void setDigestAlgorithmProvider(final String digestAlgorithmProvider)
    {
        this.digestAlgorithmProvider = digestAlgorithmProvider;
    }

    /**
     * @param pathSegments
     *            the pathSegments to set
     */
    public void setPathSegments(final int pathSegments)
    {
        if (pathSegments < 0)
        {
            throw new IllegalArgumentException("Only non-negative number of path segments are allowed");
        }
        this.pathSegments = pathSegments;
    }

    /**
     * @param bytesPerPathSegment
     *            the bytesPerPathSegment to set
     */
    public void setBytesPerPathSegment(final int bytesPerPathSegment)
    {
        if (bytesPerPathSegment <= 0)
        {
            throw new IllegalArgumentException("Only positive number of bytes per path segment are allowed");
        }
        this.bytesPerPathSegment = bytesPerPathSegment;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public boolean isContentUrlSupported(final String contentUrl)
    {
        boolean result;
        if (contentUrl != null && contentUrl.startsWith(this.dummyUrlPrefix))
        {
            result = true;
        }
        else
        {
            result = super.isContentUrlSupported(contentUrl);
        }
        return result;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public ContentWriter getWriter(final ContentContext context)
    {
        ContentWriter writer;
        if (this.isSpecialHandlingRequired(context))
        {
            LOGGER.debug("Creating deduplication enabled writer for context {} in store {}", context, this);
            final String dummyContentUrl = this.dummyUrlPrefix + UUID.randomUUID();
            writer = new DeduplicatingContentWriter(dummyContentUrl, context, this.temporaryStore, this.backingStore, this.digestAlgorithm,
                    this.digestAlgorithmProvider, this.pathSegments, this.bytesPerPathSegment);
        }
        else
        {
            LOGGER.debug("Context {} does not match configured conditions for deduplication in store {}", context, this);
            writer = super.getWriter(context);
        }
        return writer;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public boolean delete(final String contentUrl)
    {
        boolean result;
        if (contentUrl != null && contentUrl.startsWith(this.dummyUrlPrefix))
        {
            result = true;
        }
        else
        {
            result = super.delete(contentUrl);
        }
        return result;
    }
}
