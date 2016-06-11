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

import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Collection;

import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Axel Faust, <a href="http://www.prodyna.com">PRODYNA AG</a>
 */
public class CompressingContentReader extends ContentReaderFacade
{

    private static final Logger LOGGER = LoggerFactory.getLogger(CompressingContentReader.class);

    private static final CompressorStreamFactory COMPRESSOR_STREAM_FACTORY = new CompressorStreamFactory();

    protected final String compressionType;

    protected final Collection<String> mimetypesToCompress;

    protected CompressingContentReader(final ContentReader delegate, final String compressionType, final Collection<String> mimetypesToCompress)
    {
        super(delegate);

        this.compressionType = compressionType;
        this.mimetypesToCompress = mimetypesToCompress;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentReader getReader() throws ContentIOException
    {
        return new CompressingContentReader(this.delegate.getReader(), this.compressionType, this.mimetypesToCompress);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("resource")
    @Override
    public synchronized ReadableByteChannel getReadableChannel() throws ContentIOException
    {
        final String mimetype = this.getMimetype();

        final boolean shouldCompress = this.mimetypesToCompress == null || this.mimetypesToCompress.isEmpty()
                || (mimetype != null && (this.mimetypesToCompress.contains(mimetype) || this.isMimetypeToCompressWildcardMatch(mimetype)));

        ReadableByteChannel channel;
        if (shouldCompress)
        {
            LOGGER.debug("Content will be decompressed from backing store (url={})", this.getContentUrl());
            final ReadableByteChannel directChannel = this.getDirectReadableChannel();
            this.channel = this.getCallbackReadableChannel(directChannel, this.listeners);

            super.channelOpened();

            channel = this.channel;
        }
        else
        {
            LOGGER.debug("Content will not be decompressed from backing store (url={})", this.getContentUrl());
            channel = super.getReadableChannel();
        }

        return channel;
    }

    @SuppressWarnings("resource")
    protected ReadableByteChannel getDirectReadableChannel() throws ContentIOException
    {
        try
        {
            final String compressiongType = this.compressionType != null && !this.compressionType.trim().isEmpty() ? this.compressionType
                    : CompressorStreamFactory.GZIP;
            final CompressorInputStream is = COMPRESSOR_STREAM_FACTORY.createCompressorInputStream(compressiongType,
                    this.delegate.getContentInputStream());
            final ReadableByteChannel channel = Channels.newChannel(is);
            return channel;
        }
        catch (final Throwable e)
        {
            throw new ContentIOException("Failed to open channel: " + this, e);
        }
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
