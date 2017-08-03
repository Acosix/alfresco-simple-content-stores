/*
 * Copyright 2017 Acosix GmbH
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

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import org.alfresco.repo.content.filestore.FileContentWriter;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentStreamListener;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.MimetypeService;
import org.alfresco.service.cmr.repository.MimetypeServiceAware;
import org.alfresco.util.TempFileProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.FileCopyUtils;

/**
 * @author Axel Faust, <a href="http://acosix.de">Acosix GmbH</a>
 */
public class ContentWriterFacade extends ContentAccessorFacade<ContentWriter> implements ContentWriter, MimetypeServiceAware
{

    protected static class SpoofStreamListener implements ContentStreamListener
    {

        protected final ContentWriter actualWriter;

        protected final WritableByteChannel writableChannel;

        protected final FileContentWriter spoofWriter;

        protected SpoofStreamListener(final ContentWriter actualWriter, final WritableByteChannel writableChannel,
                final FileContentWriter spoofWriter)
        {
            this.actualWriter = actualWriter;
            this.writableChannel = writableChannel;
            this.spoofWriter = spoofWriter;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void contentStreamClosed() throws ContentIOException
        {
            // the spoofed temp channel has been closed, so get a new reader for it
            final ContentReader spoofReader = this.spoofWriter.getReader();
            final FileChannel spoofChannel = spoofReader.getFileChannel();
            // upload all the temp content to the real underlying channel
            try
            {
                final long spoofFileSize = spoofChannel.size();
                spoofChannel.transferTo(0, spoofFileSize, this.writableChannel);
            }
            catch (final IOException e)
            {
                LOGGER.error("Content writer {} failed to copy from spoofed temporary channel for file {}", this.actualWriter, spoofReader,
                        e);
                throw new ContentIOException("Failed to copy from spoofed temporary channel to permanent channel: \n\twriter: "
                        + this.actualWriter + "\n\ttemp: " + spoofReader, e);
            }
            finally
            {
                try
                {
                    spoofChannel.close();
                }
                catch (final Throwable e)
                {
                }
                try
                {
                    this.writableChannel.close();
                }
                catch (final IOException e)
                {
                    throw new ContentIOException("Failed to close underlying channel", e);
                }
            }
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ContentWriterFacade.class);

    protected final ContentReader existingContentReader;

    public ContentWriterFacade(final ContentWriter delegate, final ContentReader existingContentReader)
    {
        super(delegate);
        this.existingContentReader = existingContentReader;
    }

    protected ContentWriterFacade(final ContentReader existingContentReader)
    {
        super();
        this.existingContentReader = existingContentReader;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentReader getReader() throws ContentIOException
    {
        this.ensureDelegate();
        return this.delegate.getReader();
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public boolean isClosed()
    {
        this.ensureDelegate();
        return this.delegate.isClosed();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WritableByteChannel getWritableChannel() throws ContentIOException
    {
        this.ensureDelegate();
        return this.delegate.getWritableChannel();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FileChannel getFileChannel(final boolean truncate) throws ContentIOException
    {
        final WritableByteChannel writableChannel = this.getWritableChannel();
        final FileChannel fileChannel;

        // since specific facade sub-classes may adapt getWritableChannel to facade the channel from delegate we should handle file channel
        // support here for consistency (else we would have to implement this in each sub-class)

        // the following has been taken from AbstractContentWriter to spoof FileChannel (with various modifications)
        if (writableChannel instanceof FileChannel)
        {
            fileChannel = (FileChannel) writableChannel;

            if (!truncate && this.existingContentReader != null)
            {
                final ReadableByteChannel existingContentChannel = this.existingContentReader.getReadableChannel();
                final long existingContentLength = this.existingContentReader.getSize();
                try
                {
                    fileChannel.transferFrom(existingContentChannel, 0, existingContentLength);
                    LOGGER.debug("Content writer {} copied content from {} to enable random access", this, this.existingContentReader);
                }
                catch (final IOException e)
                {
                    LOGGER.error("Content writer {} failed to copy content from {} to enable random access", this,
                            this.existingContentReader, e);
                    throw new ContentIOException("Failed to copy from existing content to enable random access: \n\twriter: " + this
                            + "\n\texisting: " + this.existingContentReader, e);
                }
                finally
                {
                    try
                    {
                        existingContentChannel.close();
                    }
                    catch (final IOException e)
                    {
                    }
                }
            }
            LOGGER.debug("Content writer {} provided direct support for FileChannel", this);
        }
        else
        {

            final File tempFile = TempFileProvider.createTempFile("random_write_spoof_", ".bin");
            final FileContentWriter spoofWriter = new FileContentWriter(tempFile, this.existingContentReader);

            final ContentStreamListener spoofListener = new SpoofStreamListener(this, writableChannel, spoofWriter);
            spoofWriter.addListener(spoofListener);

            LOGGER.debug("Content writer {} provided indirect support for FileChannel via {}", this, spoofWriter);

            fileChannel = spoofWriter.getFileChannel(truncate);
        }
        return fileChannel;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OutputStream getContentOutputStream() throws ContentIOException
    {
        final WritableByteChannel channel = this.getWritableChannel();
        final OutputStream os = new BufferedOutputStream(Channels.newOutputStream(channel));
        return os;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putContent(final ContentReader reader) throws ContentIOException
    {
        this.putContent(reader.getContentInputStream());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putContent(final InputStream is) throws ContentIOException
    {
        try
        {
            final OutputStream os = this.getContentOutputStream();
            FileCopyUtils.copy(is, os);
        }
        catch (final IOException e)
        {
            LOGGER.error("Content writer {} failed to copy content from input stream", this, e);
            throw new ContentIOException("Failed to copy content from input stream: \n\twriter: " + this, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putContent(final File file) throws ContentIOException
    {
        try
        {
            final FileInputStream fis = new FileInputStream(file);
            try
            {
                this.putContent(fis);
            }
            finally
            {
                try
                {
                    fis.close();
                }
                catch (final IOException ignore)
                {
                    // NO-OP
                }
            }
        }
        catch (final IOException e)
        {
            LOGGER.error("Content writer {} failed to copy content from file {}", this, file, e);
            throw new ContentIOException("Failed to copy content from file: \n\twriter: " + this + "\n\tfile: " + file, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putContent(final String content) throws ContentIOException
    {
        try
        {
            // attempt to use the correct encoding
            final String encoding = this.getEncoding();
            byte[] bytes;
            if (encoding == null)
            {
                // Use the system default, and record what that was
                final String systemEncoding = System.getProperty("file.encoding");
                bytes = content.getBytes(systemEncoding);
                this.setEncoding(systemEncoding);
            }
            else
            {
                // Use the encoding that they specified
                bytes = content.getBytes(encoding);
            }

            // get the stream
            final ByteArrayInputStream is = new ByteArrayInputStream(bytes);
            this.putContent(is);
        }
        catch (final IOException e)
        {
            LOGGER.error("Content writer {} failed to copy content from string of length {}", this, content.length(), e);
            throw new ContentIOException(
                    "Failed to copy content from string: \n\twriter: " + this + "\n\tcontent length: " + content.length(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void guessMimetype(final String filename)
    {
        this.ensureDelegate();
        this.delegate.guessMimetype(filename);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void guessEncoding()
    {
        this.ensureDelegate();
        this.delegate.guessEncoding();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMimetypeService(final MimetypeService mimetypeService)
    {
        this.ensureDelegate();
        if (this.delegate instanceof MimetypeServiceAware)
        {
            ((MimetypeServiceAware) this.delegate).setMimetypeService(mimetypeService);
        }

    }

}
