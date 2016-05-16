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

import org.alfresco.repo.content.ContentContext;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.content.NodeContentContext;
import org.alfresco.repo.content.UnsupportedContentUrlException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.util.ParameterCheck;

/**
 * @author Axel Faust
 */
public class PathPrefixingContentStoreFacade extends BaseContentStoreFacade<ContentStore>
{

    protected final String pathPrefix;

    public PathPrefixingContentStoreFacade(final ContentStore delegate, final String pathPrefix)
    {
        super(delegate);
        ParameterCheck.mandatoryString("pathPrefix", pathPrefix);
        this.pathPrefix = pathPrefix;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isContentUrlSupported(final String contentUrl)
    {
        boolean supported = false;
        final String backingContentUrl = this.getBackingContentUrl(contentUrl);
        supported = backingContentUrl != null && super.isContentUrlSupported(backingContentUrl);
        return supported;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean exists(final String contentUrl)
    {
        boolean exists = false;
        final String backingContentUrl = this.getBackingContentUrl(contentUrl);
        exists = backingContentUrl != null && super.exists(backingContentUrl);
        return exists;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentReader getReader(final String contentUrl)
    {
        if (!this.isContentUrlSupported(contentUrl))
        {
            throw new UnsupportedContentUrlException(this, contentUrl);
        }
        final String backingContentUrl = this.getBackingContentUrl(contentUrl);
        return new PathPrefixingContentReaderFacade(super.getReader(backingContentUrl), this.pathPrefix);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentWriter getWriter(final ContentContext context)
    {
        ContentContext actualContext;
        if (context.getContentUrl() == null)
        {
            actualContext = context;
        }
        else if (!this.isContentUrlSupported(context.getContentUrl()))
        {
            throw new UnsupportedContentUrlException(this, context.getContentUrl());
        }
        else if (context instanceof NodeContentContext)
        {
            actualContext = new NodeContentContext(context.getExistingContentReader(), this.getBackingContentUrl(context.getContentUrl()),
                    ((NodeContentContext) context).getNodeRef(), ((NodeContentContext) context).getPropertyQName());
        }
        else
        {
            actualContext = new ContentContext(context.getExistingContentReader(), this.getBackingContentUrl(context.getContentUrl()));
        }

        return new PathPrefixingContentWriterFacade(super.getWriter(actualContext), this.pathPrefix);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean delete(final String contentUrl)
    {
        boolean delete = false;
        final String backingContentUrl = this.getBackingContentUrl(contentUrl);
        delete = backingContentUrl != null && super.delete(backingContentUrl);
        return delete;
    }

    protected String getBackingContentUrl(final String contentUrl)
    {
        String backingContentUrl;

        final int indexOfProtocolSeparator = contentUrl.indexOf("://");
        final int indexOfFirstPathSeparator = contentUrl.indexOf('/', indexOfProtocolSeparator + 3);
        final String firstPathSegment = contentUrl.substring(indexOfProtocolSeparator + 3, indexOfFirstPathSeparator);

        if (firstPathSegment.equals(this.pathPrefix))
        {
            backingContentUrl = contentUrl.substring(0, indexOfProtocolSeparator + 3) + contentUrl.substring(indexOfFirstPathSeparator + 1);
        }
        else
        {
            backingContentUrl = null;
        }
        return backingContentUrl;
    }

}
