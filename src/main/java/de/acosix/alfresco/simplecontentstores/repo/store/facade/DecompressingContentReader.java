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

import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Collection;

import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Axel Faust
 */
public class DecompressingContentReader extends ContentReaderFacade
{

    private static final Logger LOGGER = LoggerFactory.getLogger(DecompressingContentReader.class);

    private static final CompressorStreamFactory COMPRESSOR_STREAM_FACTORY = new CompressorStreamFactory();

    protected final String compressionType;

    protected final Collection<String> mimetypesToCompress;

    protected final long properSize;

    protected DecompressingContentReader(final ContentReader delegate, final String compressionType,
            final Collection<String> mimetypesToCompress, final long properSize)
    {
        super(delegate);

        this.compressionType = compressionType;
        this.mimetypesToCompress = mimetypesToCompress;

        this.properSize = properSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentReader getReader() throws ContentIOException
    {
        this.ensureDelegate();
        return new DecompressingContentReader(this.delegate.getReader(), this.compressionType, this.mimetypesToCompress, this.properSize);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getSize()
    {
        final long size = this.properSize > 0 ? this.properSize : super.getSize();
        return size;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized ReadableByteChannel getReadableChannel() throws ContentIOException
    {
        this.ensureDelegate();
        final String mimetype = this.getMimetype();

        LOGGER.debug("Determined mimetype {} as provided via setter / content data - mimetypes to compress are {}", mimetype,
                this.mimetypesToCompress);

        final boolean shouldCompress = this.mimetypesToCompress == null || this.mimetypesToCompress.isEmpty()
                || (mimetype != null && (this.mimetypesToCompress.contains(mimetype) || this.isMimetypeToCompressWildcardMatch(mimetype)));

        ReadableByteChannel channel;
        if (shouldCompress)
        {
            LOGGER.debug("Content will be decompressed from backing store (url={})", this.getContentUrl());

            final String compressiongType = this.compressionType != null && !this.compressionType.trim().isEmpty() ? this.compressionType
                    : CompressorStreamFactory.GZIP;
            try
            {
                final CompressorInputStream is = COMPRESSOR_STREAM_FACTORY.createCompressorInputStream(compressiongType,
                        this.delegate.getContentInputStream());
                channel = Channels.newChannel(is);
            }
            catch (final CompressorException e)
            {
                LOGGER.error("Failed to open decompressing channel", e);
                throw new ContentIOException("Failed to open channel: " + this, e);
            }
        }
        else
        {
            LOGGER.debug("Content will not be decompressed from backing store (url={})", this.getContentUrl());
            channel = super.getReadableChannel();
        }

        return channel;
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
