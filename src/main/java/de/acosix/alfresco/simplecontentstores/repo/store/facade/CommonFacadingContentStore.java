/*
 * Copyright 2017, 2018 Acosix GmbH
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.alfresco.repo.content.ContentContext;
import org.alfresco.repo.content.ContentExistsException;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.content.NodeContentContext;
import org.alfresco.repo.content.UnsupportedContentUrlException;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.dictionary.PropertyDefinition;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.ParameterCheck;
import org.alfresco.util.PropertyCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

/**
 * @author Axel Faust
 */
public abstract class CommonFacadingContentStore implements ContentStore, InitializingBean
{

    private static final Logger LOGGER = LoggerFactory.getLogger(CommonFacadingContentStore.class);

    protected NamespaceService namespaceService;

    protected DictionaryService dictionaryService;

    protected List<String> handleContentPropertyNames;

    protected transient Set<QName> handleContentPropertyQNames;

    protected ContentStore backingStore;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "namespaceService", this.namespaceService);
        PropertyCheck.mandatory(this, "dictionaryService", this.dictionaryService);
        PropertyCheck.mandatory(this, "backingStore", this.backingStore);

        this.afterPropertiesSet_setupHandleContentProperties();
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
     * @param handleContentPropertyNames
     *            the handleContentPropertyNames to set
     */
    public void setHandleContentPropertyNames(final List<String> handleContentPropertyNames)
    {
        this.handleContentPropertyNames = handleContentPropertyNames;
    }

    /**
     * @param backingStore
     *            the backingStore to set
     */
    public void setBackingStore(final ContentStore backingStore)
    {
        this.backingStore = backingStore;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public boolean isContentUrlSupported(final String contentUrl)
    {
        return this.backingStore.isContentUrlSupported(contentUrl);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public boolean isWriteSupported()
    {
        return this.backingStore.isWriteSupported();
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public long getSpaceFree()
    {
        return this.backingStore.getSpaceFree();
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public long getSpaceTotal()
    {
        return this.backingStore.getSpaceTotal();
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public String getRootLocation()
    {
        return this.backingStore.getRootLocation();
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public boolean exists(final String contentUrl)
    {
        return this.backingStore.exists(contentUrl);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public ContentReader getReader(final String contentUrl)
    {
        return this.backingStore.getReader(contentUrl);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public ContentWriter getWriter(final ContentContext context)
    {
        if (!this.isWriteSupported())
        {
            LOGGER.debug("Write requests are not supported for this store:\n\tStore:   {}\n\tContext: {}", this, context);
            throw new UnsupportedOperationException("Write operations are not supported by this store: " + this);
        }

        final String contentUrl = context.getContentUrl();
        if (contentUrl != null)
        {
            if (!this.isContentUrlSupported(contentUrl))
            {
                LOGGER.debug("Specific writer content URL is unsupported: \n\tStore:   {}\n\tContext: {}", this, context);
                throw new UnsupportedContentUrlException(this, contentUrl);
            }
            else if (this.exists(contentUrl))
            {
                LOGGER.debug("The content location is already used: \n\tStore:   {}\n\tContext: {}", this, context);
                throw new ContentExistsException(this, contentUrl);
            }
        }

        return this.backingStore.getWriter(context);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public boolean delete(final String contentUrl)
    {
        return this.backingStore.delete(contentUrl);
    }

    protected boolean isSpecialHandlingRequired(final ContentContext ctx)
    {
        final QName contentPropertyQName = ctx instanceof NodeContentContext ? ((NodeContentContext) ctx).getPropertyQName() : null;
        final boolean result = this.handleContentPropertyQNames == null || this.handleContentPropertyQNames.contains(contentPropertyQName);
        return result;
    }

    private void afterPropertiesSet_setupHandleContentProperties()
    {
        if (this.handleContentPropertyNames != null && !this.handleContentPropertyNames.isEmpty())
        {
            this.handleContentPropertyQNames = new HashSet<>();
            for (final String facadePropertyName : this.handleContentPropertyNames)
            {
                final QName routePropertyQName = QName.resolveToQName(this.namespaceService, facadePropertyName);
                ParameterCheck.mandatory("routePropertyQName", routePropertyQName);

                final PropertyDefinition contentPropertyDefinition = this.dictionaryService.getProperty(routePropertyQName);
                if (contentPropertyDefinition == null
                        || !DataTypeDefinition.CONTENT.equals(contentPropertyDefinition.getDataType().getName()))
                {
                    throw new IllegalStateException(facadePropertyName + " is not a valid content model property of type d:content");
                }
                this.handleContentPropertyQNames.add(routePropertyQName);
            }
        }
    }
}
