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

import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.util.ParameterCheck;

/**
 * @author Axel Faust
 */
public class PathPrefixingContentWriterFacade extends BaseContentWriterFacade
{

    protected final String pathPrefix;

    protected transient String contentUrl;

    public PathPrefixingContentWriterFacade(final ContentWriter delegate, final String pathPrefix)
    {
        super(delegate);
        ParameterCheck.mandatoryString("pathPrefix", pathPrefix);
        this.pathPrefix = pathPrefix;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentData getContentData()
    {
        final ContentData originalContentData = super.getContentData();
        final ContentData contentData = new ContentData(this.getContentUrl(), originalContentData.getMimetype(),
                originalContentData.getSize(), originalContentData.getEncoding());
        return contentData;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getContentUrl()
    {
        if (this.contentUrl == null)
        {
            final String originalContentUrl = super.getContentUrl();
            final int indexOfProtocolSeparator = originalContentUrl.indexOf("://");
            this.contentUrl = originalContentUrl.substring(0, indexOfProtocolSeparator + 3) + this.pathPrefix + "/"
                    + originalContentUrl.substring(indexOfProtocolSeparator + 3);
        }

        return this.contentUrl;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentReader getReader() throws ContentIOException
    {
        return new PathPrefixingContentReaderFacade(this.delegate.getReader(), this.pathPrefix);
    }

}
