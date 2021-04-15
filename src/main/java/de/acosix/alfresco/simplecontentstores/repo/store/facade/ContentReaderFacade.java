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

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.List;

import org.alfresco.repo.content.filestore.FileContentWriter;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentStreamListener;
import org.alfresco.util.TempFileProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.FileCopyUtils;

/**
 * @author Axel Faust
 */
public class ContentReaderFacade extends ContentAccessorFacade<ContentReader> implements ContentReader
{

    private static final Logger LOGGER = LoggerFactory.getLogger(ContentReaderFacade.class);

    protected final List<ContentStreamListener> listeners = new ArrayList<>();

    public ContentReaderFacade(final ContentReader delegate)
    {
        super(delegate);
    }

    protected ContentReaderFacade()
    {
        super();
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
     * {@inheritDoc}
     */
    @Override
    public boolean exists()
    {
        this.ensureDelegate();
        return this.delegate.exists();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getLastModified()
    {
        this.ensureDelegate();
        return this.delegate.getLastModified();
    }

    /**
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
    public ReadableByteChannel getReadableChannel() throws ContentIOException
    {
        this.ensureDelegate();
        return this.delegate.getReadableChannel();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FileChannel getFileChannel() throws ContentIOException
    {
        final ReadableByteChannel readableChannel = this.getReadableChannel();
        final FileChannel fileChannel;

        // since specific facade sub-classes may adapt getReadableChannel to facade the channel from delegate we should handle file channel
        // support here for consistency (else we would have to implement this in each sub-class)

        // the following has been taken from AbstractContentReader to spoof FileChannel (with various modifications)

        if (readableChannel instanceof FileChannel)
        {
            fileChannel = (FileChannel) readableChannel;
            LOGGER.debug("Content reader {} provided direct support for FileChannel", this);
        }
        else
        {
            final File tempFile = TempFileProvider.createTempFile("random_read_spoof_", ".bin");
            final FileContentWriter spoofWriter = new FileContentWriter(tempFile);
            final FileChannel spoofWriterChannel = spoofWriter.getFileChannel(false);
            try
            {
                final long spoofFileSize = this.getSize();
                spoofWriterChannel.transferFrom(readableChannel, 0, spoofFileSize);
                LOGGER.debug("Content reader {} copied content to enable random access", this);
            }
            catch (final IOException e)
            {
                LOGGER.error("Content reader {} failed to copy content to enable random access", this, e);
                throw new ContentIOException("Failed to copy from permanent channel to spoofed temporary channel: \n\treader: " + this
                        + "\n\ttemp: " + spoofWriter, e);
            }
            finally
            {
                try
                {
                    spoofWriterChannel.close();
                }
                catch (final IOException e)
                {
                    LOGGER.debug("Error closing spoofed writer channel", e);
                }
            }
            final ContentReader spoofReader = spoofWriter.getReader();
            final ContentStreamListener spoofListener = () -> {
                try
                {
                    readableChannel.close();
                }
                catch (final IOException e)
                {
                    throw new ContentIOException("Failed to close underlying channel", e);
                }
            };
            spoofReader.addListener(spoofListener);
            fileChannel = spoofReader.getFileChannel();
            LOGGER.debug("Content reader {} provided indirect support for FileChannel via {}", this, spoofWriter);
        }

        return fileChannel;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStream getContentInputStream() throws ContentIOException
    {
        try
        {
            final ReadableByteChannel channel = this.getReadableChannel();
            InputStream is = Channels.newInputStream(channel);
            is = new BufferedInputStream(is);
            return is;
        }
        catch (final Throwable e)
        {
            LOGGER.error("Failed to open stream onto channel for reader {}", this, e);
            throw new ContentIOException("Failed to open stream onto channel: \n   accessor: " + this, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    // same implementation as AbstractContentReader
    public void getContent(final OutputStream os) throws ContentIOException
    {
        try
        {
            final InputStream is = this.getContentInputStream();
            FileCopyUtils.copy(is, os);
        }
        catch (final IOException e)
        {
            throw new ContentIOException("Failed to copy content to output stream: \n" + "   accessor: " + this, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    // overriden with same implementation as AbstractContentReader
    public void getContent(final File file) throws ContentIOException
    {
        try
        {
            final InputStream is = this.getContentInputStream();
            final FileOutputStream os = new FileOutputStream(file);
            FileCopyUtils.copy(is, os);
        }
        catch (final IOException e)
        {
            throw new ContentIOException("Failed to copy content to file: \n" + "   accessor: " + this + "\n" + "   file: " + file, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    // same implementation as AbstractContentReader
    public String getContentString() throws ContentIOException
    {
        try
        {
            // read from the stream into a byte[]
            final InputStream is = this.getContentInputStream();
            final ByteArrayOutputStream os = new ByteArrayOutputStream();
            FileCopyUtils.copy(is, os); // both streams are closed
            final byte[] bytes = os.toByteArray();
            // get the encoding for the string
            final String encoding = this.getEncoding();
            // create the string from the byte[] using encoding if necessary
            final String systemEncoding = System.getProperty("file.encoding");
            final String content = (encoding == null) ? new String(bytes, systemEncoding) : new String(bytes, encoding);
            // done
            return content;
        }
        catch (final IOException e)
        {
            throw new ContentIOException("Failed to copy content to string: \n" + "   accessor: " + this, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    // same implementation as AbstractContentReader
    public String getContentString(final int length) throws ContentIOException
    {
        if (length <= 0)
        {
            throw new IllegalArgumentException("Character count must be positive and within range");
        }

        Reader reader = null;
        try
        {
            // just create buffer of the required size
            final char[] buffer = new char[length];

            final String encoding = this.getEncoding();
            // create a reader from the input stream
            if (encoding == null)
            {
                final String systemEncoding = System.getProperty("file.encoding");
                reader = new InputStreamReader(this.getContentInputStream(), systemEncoding);
            }
            else
            {
                reader = new InputStreamReader(this.getContentInputStream(), encoding);
            }
            // read it all, if possible
            final int count = reader.read(buffer, 0, length);

            // there may have been fewer characters - create a new string as the result
            return (count != -1 ? new String(buffer, 0, count) : "");
        }
        catch (final IOException e)
        {
            throw new ContentIOException("Failed to copy content to string: \n\taccessor: " + this + "\n\tlength: " + length, e);
        }
        finally
        {
            if (reader != null)
            {
                try
                {
                    reader.close();
                }
                catch (final Throwable e)
                {
                    LOGGER.debug("Failed to close reader", e);
                }
            }
        }
    }
}
