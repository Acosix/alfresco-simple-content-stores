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

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.security.Key;

import org.alfresco.repo.content.ContentContext;
import org.alfresco.repo.content.EmptyContentReader;
import org.alfresco.repo.content.encoding.ContentCharsetFinder;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.MimetypeService;
import org.alfresco.util.ParameterCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Axel Faust
 */
public class EncryptingContentWriterFacade extends ContentWriterFacade
{

    private static final Logger LOGGER = LoggerFactory.getLogger(EncryptingContentWriterFacade.class);

    protected final ContentContext context;

    protected final Key key;

    protected MimetypeService mimetypeService;

    protected boolean completedWrite = false;

    protected boolean guessMimetype = false;

    protected boolean guessEncoding = false;

    protected String guessFileName;

    protected long unencryptedSize;

    protected long encryptedSize;

    protected EncryptingContentWriterFacade(final ContentWriter delegate, final ContentContext context, final Key key,
            final ContentReader existingContentReader)
    {
        super(delegate, existingContentReader);

        ParameterCheck.mandatory("context", context);
        ParameterCheck.mandatory("key", key);

        this.addListener(() -> {
            EncryptingContentWriterFacade.this.completedWrite = true;

            if (EncryptingContentWriterFacade.this.guessMimetype)
            {
                EncryptingContentWriterFacade.this.guessMimetype(EncryptingContentWriterFacade.this.guessFileName);
            }

            if (EncryptingContentWriterFacade.this.guessEncoding)
            {
                EncryptingContentWriterFacade.this.guessEncoding();
            }
        });

        this.context = context;
        this.key = key;
    }

    /**
     * @param mimetypeService
     *            the mimetypeService to set
     */
    @Override
    public void setMimetypeService(final MimetypeService mimetypeService)
    {
        super.setMimetypeService(mimetypeService);
        this.mimetypeService = mimetypeService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getSize()
    {
        final long size;

        if (this.completedWrite)
        {
            size = this.unencryptedSize;
        }
        else
        {
            size = super.getSize();
        }

        return size;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentData getContentData()
    {
        final ContentData contentData = super.getContentData();
        final ContentData updatedData;
        if (this.completedWrite)
        {
            // correct size
            updatedData = new ContentData(contentData.getContentUrl(), contentData.getMimetype(), this.unencryptedSize,
                    contentData.getEncoding(), contentData.getLocale());
        }
        else
        {
            updatedData = contentData;
        }
        return updatedData;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentReader getReader() throws ContentIOException
    {
        final ContentReader reader;
        if (this.completedWrite)
        {
            reader = new DecryptingContentReaderFacade(super.getReader(), this.key, this.unencryptedSize);
        }
        else
        {
            reader = new EmptyContentReader(this.getContentUrl());
        }

        return reader;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WritableByteChannel getWritableChannel() throws ContentIOException
    {
        final WritableByteChannel channel = super.getWritableChannel();
        final EncryptingWritableByteChannel eChannel = new EncryptingWritableByteChannel(channel, this.key);

        eChannel.addListener((bytesRead, bytesWritten) -> {
            EncryptingContentWriterFacade.this.unencryptedSize += bytesRead;
            EncryptingContentWriterFacade.this.encryptedSize += bytesWritten;
        });

        return eChannel;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void guessEncoding()
    {
        if (this.completedWrite)
        {
            if (this.mimetypeService == null)
            {
                LOGGER.warn("MimetypeService not supplied, but required for content guessing");
                return;
            }

            final ContentCharsetFinder charsetFinder = this.mimetypeService.getContentCharsetFinder();

            final ContentReader reader = this.getReader();
            final InputStream is = reader.getContentInputStream();
            final Charset charset = charsetFinder.getCharset(is, this.getMimetype());
            try
            {
                is.close();
            }
            catch (final IOException e)
            {
                LOGGER.trace("Error closing input stream");
            }

            this.setEncoding(charset.name());
        }
        else
        {
            // no point in delegating to backing writer - it can't guess from encrypted content
            this.guessEncoding = true;
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void guessMimetype(final String filename)
    {
        if (this.completedWrite)
        {
            if (this.mimetypeService == null)
            {
                LOGGER.warn("MimetypeService not supplied, but required for content guessing");
                return;
            }

            String mimetype;
            // TODO Why do 5.1/5.2 include this special check here and not in mimetypeService?
            if (filename != null && filename.startsWith("._"))
            {
                mimetype = "application/applefile";
            }
            else
            {
                mimetype = this.mimetypeService.guessMimetype(filename, this.getReader());
            }
            this.setMimetype(mimetype);
        }
        else
        {
            // no point in delegating to backing writer - it can't guess from encrypted content
            this.guessMimetype = true;
            this.guessFileName = filename;
        }
    }
}
