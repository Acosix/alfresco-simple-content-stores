/*
 * Copyright 2017 - 2021 Acosix GmbH
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Set;

import org.alfresco.repo.content.AbstractContentWriter;
import org.alfresco.repo.content.ContentContext;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.content.NodeContentContext;
import org.alfresco.repo.content.filestore.FileContentWriter;
import org.alfresco.repo.transaction.TransactionalResourceHelper;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentStreamListener;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.MimetypeService;
import org.alfresco.service.cmr.repository.MimetypeServiceAware;
import org.alfresco.util.EqualsHelper;
import org.alfresco.util.ParameterCheck;
import org.alfresco.util.transaction.TransactionSupportUtil;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.acosix.alfresco.simplecontentstores.repo.store.StoreConstants;
import de.acosix.alfresco.simplecontentstores.repo.store.context.ContentStoreContext;
import de.acosix.alfresco.simplecontentstores.repo.store.context.ContentStoreContext.ContentStoreContextRestorator;

/**
 * @author Axel Faust
 */
public class DeduplicatingContentWriter extends AbstractContentWriter implements ContentStreamListener
{

    private static final Logger LOGGER = LoggerFactory.getLogger(DeduplicatingContentWriter.class);

    protected final ContentStoreContextRestorator<Void> contextRestorator = ContentStoreContext.getContextRestorationHandle();

    protected final ContentContext context;

    protected final String digestAlgorithm;

    protected final String digestAlgorithmProvider;

    protected final int pathSegments;

    protected final int bytesPerPathSegment;

    protected final ContentStore temporaryContentStore;

    protected final ContentStore backingContentStore;

    protected final ContentWriter temporaryWriter;

    protected final String originalContentUrl;

    protected String digestHex;

    protected String deduplicatedContentUrl;

    protected MimetypeService mimetypeService;

    protected DeduplicatingContentWriter(final String contentUrl, final ContentContext context, final ContentStore temporaryContentStore,
            final ContentStore backingContentStore, final String digestAlgorithm, final String digestAlgorithmProvider,
            final int pathSegments, final int bytesPerPathSegment)
    {
        super(contentUrl, context.getExistingContentReader());

        ParameterCheck.mandatory("context", context);
        ParameterCheck.mandatory("temporaryContentStore", temporaryContentStore);
        ParameterCheck.mandatory("backingContentStore", backingContentStore);

        if (pathSegments < 0 || bytesPerPathSegment <= 0)
        {
            throw new IllegalArgumentException(
                    "Only non-negative number of path segments and positive number of bytes per path segment are allowed");
        }

        this.context = context;
        this.temporaryContentStore = temporaryContentStore;
        this.backingContentStore = backingContentStore;

        this.digestAlgorithm = digestAlgorithm;
        this.digestAlgorithmProvider = digestAlgorithmProvider;

        this.pathSegments = pathSegments;
        this.bytesPerPathSegment = bytesPerPathSegment;

        this.originalContentUrl = contentUrl;

        // we are the first real listener (DoGuessingOnCloseListener always is first)
        super.addListener(this);

        final ContentContext temporaryContext = new ContentContext(context.getExistingContentReader(), null);
        this.temporaryWriter = this.temporaryContentStore.getWriter(temporaryContext);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getSize()
    {
        final long size = this.getReader().getSize();
        return size;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void contentStreamClosed()
    {
        // should never happen that we are called twice, but still good idea to protect against incorrect interface invocation
        if (this.deduplicatedContentUrl == null)
        {
            try
            {
                // try to de-duplicate
                this.findExistingContent();
                if (this.deduplicatedContentUrl == null)
                {
                    this.contextRestorator.withRestoredContext(() -> {
                        DeduplicatingContentWriter.this.writeToBackingStore();
                        return null;
                    });
                }
                else if (this.backingContentStore.isWriteSupported() && this.backingContentStore.exists(this.originalContentUrl))
                {
                    // we did not use the writer so delete any backend remnant that may have been pre-emptively created
                    try
                    {
                        this.backingContentStore.delete(this.originalContentUrl);
                    }
                    catch (final UnsupportedOperationException uoe)
                    {
                        LOGGER.debug("Backing content store does not support delete", uoe);
                    }
                }
            }
            finally
            {
                this.cleanupTemporaryContent();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMimetypeService(final MimetypeService mimetypeService)
    {
        this.mimetypeService = mimetypeService;
        super.setMimetypeService(mimetypeService);

        if (this.temporaryWriter instanceof MimetypeServiceAware)
        {
            ((MimetypeServiceAware) this.temporaryWriter).setMimetypeService(mimetypeService);
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected final void setContentUrl(final String contentUrl)
    {
        throw new UnsupportedOperationException("Content URL cannot be forced");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ContentReader createReader() throws ContentIOException
    {
        ContentReader reader = this.getDeduplicatedContentReader();
        if (reader == null)
        {
            // reader with faked content url to match expectation of super.getReader()
            reader = new ContentReaderFacade(this.temporaryWriter.getReader())
            {

                /**
                 * {@inheritDoc}
                 */
                @Override
                public String getContentUrl()
                {
                    return DeduplicatingContentWriter.this.getContentUrl();
                }

                /**
                 *
                 * {@inheritDoc}
                 */
                @Override
                public ContentReader getReader()
                {
                    return DeduplicatingContentWriter.this.getReader();
                }
            };
        }

        return reader;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected WritableByteChannel getDirectWritableChannel() throws ContentIOException
    {
        // need to wrap this to avoid issue of CallbackFileChannel rejection in CallbackFileChannel constructor
        final WritableByteChannel channel = new WritableByteChannel()
        {

            private final WritableByteChannel channel = DeduplicatingContentWriter.this.temporaryWriter.getWritableChannel();

            /**
             *
             * {@inheritDoc}
             */
            @Override
            public boolean isOpen()
            {
                return this.channel.isOpen();
            }

            /**
             *
             * {@inheritDoc}
             */
            @Override
            public void close() throws IOException
            {
                this.channel.close();
            }

            /**
             *
             * {@inheritDoc}
             */
            @Override
            public int write(final ByteBuffer src) throws IOException
            {
                return this.channel.write(src);
            }
        };
        return channel;
    }

    protected void cleanupTemporaryContent()
    {
        // check if we can trigger eager clean up
        // (standard temp lifetime of between 1:00 and 1:59 hours just causes too much build-up)
        if (this.temporaryWriter instanceof FileContentWriter)
        {
            final File tempFile = ((FileContentWriter) this.temporaryWriter).getFile();
            if (tempFile.exists() && !tempFile.delete())
            {
                tempFile.deleteOnExit();
            }
        }
        else
        {
            try
            {
                this.temporaryContentStore.delete(this.temporaryWriter.getContentUrl());
            }
            catch (final UnsupportedOperationException uoe)
            {
                LOGGER.debug("Temporary content store does not support delete", uoe);
            }
        }
    }

    protected ContentReader getDeduplicatedContentReader()
    {
        final ContentReader deduplicatedContentReader;
        if (this.deduplicatedContentUrl != null)
        {
            deduplicatedContentReader = this.backingContentStore.getReader(this.deduplicatedContentUrl);
        }
        else
        {
            deduplicatedContentReader = null;
        }

        return deduplicatedContentReader;
    }

    protected void findExistingContent()
    {
        if (this.digestHex == null)
        {
            final byte[] digest = this.createDigest();
            final char[] digestHex = Hex.encodeHex(digest, false);
            this.digestHex = new String(digestHex);
        }

        final String deduplicatedContentUrl = this.makeContentUrl(this.digestHex);

        if (this.backingContentStore.isContentUrlSupported(deduplicatedContentUrl))
        {
            final ContentReader reader = this.backingContentStore.getReader(deduplicatedContentUrl);
            if (reader != null && reader.exists())
            {
                // TODO lookup existing content data entity to copy mimetype + encoding
                // (mimetype and encoding must be identical to guarantee identical access behaviour, e.g. when using compressing content
                // store facade)

                this.deduplicatedContentUrl = reader.getContentUrl();
                super.setContentUrl(this.deduplicatedContentUrl);
            }
        }
    }

    protected void writeToBackingStore()
    {
        if (this.digestHex == null)
        {
            final byte[] digest = this.createDigest();
            final char[] digestHex = Hex.encodeHex(digest, false);
            this.digestHex = new String(digestHex);
        }

        final String suggestedContentUrl = this.makeContentUrl(this.digestHex);

        final ContentReader reader = this.getReader();
        final ContentContext backingContext;
        if (this.context instanceof NodeContentContext)
        {
            backingContext = new NodeContentContext(null, suggestedContentUrl, ((NodeContentContext) this.context).getNodeRef(),
                    ((NodeContentContext) this.context).getPropertyQName());
        }
        else
        {
            backingContext = new ContentContext(null, suggestedContentUrl);
        }

        final ContentWriter backingWriter = this.backingContentStore.getWriter(backingContext);
        if (backingWriter instanceof MimetypeServiceAware && this.mimetypeService != null)
        {
            ((MimetypeServiceAware) backingWriter).setMimetypeService(this.mimetypeService);
        }
        backingWriter.putContent(reader);

        // since we use a wildcard protocol in our expectation and don't know backing store protocol, do a relative match
        final String expectedRelativeUrl = suggestedContentUrl
                .substring(suggestedContentUrl.indexOf(ContentStore.PROTOCOL_DELIMITER) + ContentStore.PROTOCOL_DELIMITER.length());
        final String actualContentUrl = backingWriter.getContentUrl();
        final String actualRelativeUrl = actualContentUrl
                .substring(actualContentUrl.indexOf(ContentStore.PROTOCOL_DELIMITER) + ContentStore.PROTOCOL_DELIMITER.length());
        if (!EqualsHelper.nullSafeEquals(expectedRelativeUrl, actualRelativeUrl))
        {
            throw new IllegalStateException("Backing content store did not use the required target content URL");
        }

        this.deduplicatedContentUrl = actualContentUrl;
        super.setContentUrl(this.deduplicatedContentUrl);

        if (TransactionSupportUtil.isActualTransactionActive())
        {
            // this is a new URL so register for rollback handling
            final Set<String> urlsToDelete = TransactionalResourceHelper.getSet(StoreConstants.KEY_POST_ROLLBACK_DELETION_URLS);
            urlsToDelete.add(this.deduplicatedContentUrl);
        }
    }

    protected byte[] createDigest()
    {
        final ContentReader reader = this.getReader();

        MessageDigest digest;
        try
        {
            if (this.digestAlgorithmProvider != null && this.digestAlgorithmProvider.trim().length() > 0)
            {
                digest = MessageDigest.getInstance(this.digestAlgorithm, this.digestAlgorithmProvider);
            }
            else
            {
                digest = MessageDigest.getInstance(this.digestAlgorithm);
            }
        }
        catch (final NoSuchProviderException nspEx)
        {
            LOGGER.error("Hash algorithm provider {} is not available", this.digestAlgorithmProvider);
            throw new ContentIOException("Hash algorithm provider for deduplication not available", nspEx);
        }
        catch (final NoSuchAlgorithmException nsaEx)
        {
            LOGGER.error("Hash algorithm {} is not available", this.digestAlgorithm);
            throw new ContentIOException("Hash algorithm for deduplication not available", nsaEx);
        }

        final InputStream contentInputStream = reader.getContentInputStream();
        try
        {
            // 512kB buffer is larger than normal - assume backing storage has larger than standard block size
            final byte[] buffer = new byte[1024 * 512];
            int bytesRead = -1;
            while ((bytesRead = contentInputStream.read(buffer)) != -1)
            {
                digest.update(buffer, 0, bytesRead);
            }
        }
        catch (final IOException ioEx)
        {
            LOGGER.error("Could not read content for hashing from {}", reader);
            throw new ContentIOException("Unable to read content for de-duplication", ioEx);
        }
        finally
        {
            try
            {
                contentInputStream.close();
            }
            catch (final IOException ioEx)
            {
                LOGGER.info("Could not close input stream for {}", reader);
                // no real problem
            }
        }

        final byte[] digestBytes = digest.digest();

        return digestBytes;
    }

    protected String makeContentUrl(final String digest)
    {
        final StringBuilder contentUrlBuilder = new StringBuilder();

        contentUrlBuilder.append(StoreConstants.WILDCARD_PROTOCOL);
        contentUrlBuilder.append(ContentStore.PROTOCOL_DELIMITER);

        final int charsPerByte = this.bytesPerPathSegment * 2;
        for (int segment = 0, digestLength = digest.length(); segment < this.pathSegments
                && digestLength >= (segment + 1) * charsPerByte; segment++)
        {
            final String digestFragment = digest.substring(segment * charsPerByte, (segment + 1) * charsPerByte);
            contentUrlBuilder.append(digestFragment);
            contentUrlBuilder.append('/');
        }

        contentUrlBuilder.append(digest);
        contentUrlBuilder.append(".bin");

        return contentUrlBuilder.toString();
    }
}
