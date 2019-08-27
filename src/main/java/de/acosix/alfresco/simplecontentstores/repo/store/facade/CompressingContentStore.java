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
package de.acosix.alfresco.simplecontentstores.repo.store.facade;

import java.util.Collection;
import java.util.Set;

import org.alfresco.repo.content.ContentContext;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.transaction.AlfrescoTransactionSupport;
import org.alfresco.repo.transaction.TransactionalResourceHelper;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.util.PropertyCheck;

import de.acosix.alfresco.simplecontentstores.repo.store.StoreConstants;
import de.acosix.alfresco.simplecontentstores.repo.store.context.ContentStoreContext;

/**
 * @author Axel Faust
 */
public class CompressingContentStore extends CommonFacadingContentStore
{

    protected ContentStore temporaryStore;

    protected String compressionType;

    protected Collection<String> mimetypesToCompress;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        super.afterPropertiesSet();

        PropertyCheck.mandatory(this, "temporaryStore", this.temporaryStore);
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
     * @param compressionType
     *            the compressionType to set
     */
    public void setCompressionType(final String compressionType)
    {
        this.compressionType = compressionType;
    }

    /**
     * @param mimetypesToCompress
     *            the mimetypesToCompress to set
     */
    public void setMimetypesToCompress(final Collection<String> mimetypesToCompress)
    {
        this.mimetypesToCompress = mimetypesToCompress;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentReader getReader(final String contentUrl)
    {
        // need to use information from context (if call came via ContentService#getReader(NodeRef, QName)) to find the real size, as the
        // size reported by the reader from the delegate may differ due to compression
        // context also helps us optimise by avoiding decompressing facade if content data mimetype does not require compression at all
        long properSize = -1;
        String mimetype = null;

        final Object contentDataCandidate = ContentStoreContext.getContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_CONTENT_DATA);
        if (contentDataCandidate instanceof ContentData)
        {
            final ContentData contentData = (ContentData) contentDataCandidate;
            if (contentUrl.equals(contentData.getContentUrl()))
            {
                properSize = contentData.getSize();
                mimetype = contentData.getMimetype();
            }
        }

        // this differs from shouldCompress determination in compressing writer / decompressing reader
        // if we don't know the mimetype yet (e.g. due to missing context), we can't make the assumption that content may not need
        // decompression at this point - mimetype may still be set via setMimetype() on reader instance
        final boolean shouldCompress = this.mimetypesToCompress == null || this.mimetypesToCompress.isEmpty() || mimetype == null
                || this.mimetypesToCompress.contains(mimetype) || this.isMimetypeToCompressWildcardMatch(mimetype);

        ContentReader reader;
        final ContentReader backingReader = super.getReader(contentUrl);
        if (shouldCompress)
        {
            reader = new DecompressingContentReader(backingReader, this.compressionType, this.mimetypesToCompress, properSize);
        }
        else
        {
            reader = backingReader;
        }

        return reader;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentWriter getWriter(final ContentContext context)
    {
        final ContentWriter backingWriter = super.getWriter(context);

        if (AlfrescoTransactionSupport.isActualTransactionActive())
        {
            // this is a new URL so register for rollback handling
            final Set<String> urlsToDelete = TransactionalResourceHelper.getSet(StoreConstants.KEY_POST_ROLLBACK_DELETION_URLS);
            urlsToDelete.add(backingWriter.getContentUrl());
        }

        final ContentWriter writer = new CompressingContentWriter(backingWriter.getContentUrl(), context, this.temporaryStore,
                backingWriter, this.compressionType, this.mimetypesToCompress);
        return writer;
    }

    protected boolean isMimetypeToCompressWildcardMatch(final String mimetype)
    {
        boolean isMatch = false;
        for (final String mimetypeToCompress : this.mimetypesToCompress)
        {
            if (mimetypeToCompress.endsWith("/*"))
            {
                if (mimetype.startsWith(mimetypeToCompress.substring(0, mimetypeToCompress.length() - 1)))
                {
                    isMatch = true;
                    break;
                }
            }
        }
        return isMatch;
    }
}
