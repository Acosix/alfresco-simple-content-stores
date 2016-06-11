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
import java.util.Locale;

import org.alfresco.repo.content.AbstractContentAccessor;
import org.alfresco.repo.content.filestore.FileContentWriter;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentStreamListener;
import org.alfresco.util.ParameterCheck;
import org.alfresco.util.TempFileProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.util.FileCopyUtils;

/**
 * @author Axel Faust
 */
public class ContentReaderFacade extends AbstractContentAccessor implements ContentReader
{

    private static final Logger LOGGER = LoggerFactory.getLogger(ContentReaderFacade.class);

    protected final ContentReader delegate;

    protected final List<ContentStreamListener> listeners = new ArrayList<ContentStreamListener>();

    protected ReadableByteChannel channel;

    public ContentReaderFacade(final ContentReader delegate)
    {
        super(delegate.getContentUrl());
        ParameterCheck.mandatory("delegate", delegate);
        this.delegate = delegate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isChannelOpen()
    {
        final boolean isOpen = (this.channel != null && this.channel.isOpen()) || this.delegate.isChannelOpen();
        return isOpen;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addListener(final ContentStreamListener listener)
    {
        if (this.channel != null)
        {
            throw new RuntimeException("Channel is already in use");
        }
        this.listeners.add(listener);
        this.delegate.addListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentReader getReader() throws ContentIOException
    {
        return this.delegate.getReader();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getSize()
    {
        return this.delegate.getSize();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean exists()
    {
        return this.delegate.exists();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentData getContentData()
    {
        return this.delegate.getContentData();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getLastModified()
    {
        return this.delegate.getLastModified();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getContentUrl()
    {
        return this.delegate.getContentUrl();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isClosed()
    {
        final boolean isClosed = (this.channel != null && !this.channel.isOpen()) || this.delegate.isClosed();
        return isClosed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getMimetype()
    {
        return this.delegate.getMimetype();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMimetype(final String mimetype)
    {
        this.delegate.setMimetype(mimetype);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized ReadableByteChannel getReadableChannel() throws ContentIOException
    {
        if (this.channel != null)
        {
            throw new RuntimeException("A channel has already been opened");
        }

        return this.delegate.getReadableChannel();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getEncoding()
    {
        return this.delegate.getEncoding();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEncoding(final String encoding)
    {
        this.delegate.setEncoding(encoding);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("resource")
    @Override
    // overriden with same implementation as AbstractContentReader
    public synchronized FileChannel getFileChannel() throws ContentIOException
    {
        /*
         * Where the underlying support is not present for this method, a temporary file will be used as a substitute. When the write is
         * complete, the results are copied directly to the underlying channel.
         */

        // get the underlying implementation's best readable channel
        this.channel = this.getReadableChannel();
        // now use this channel if it can provide the random access, otherwise spoof it
        FileChannel clientFileChannel = null;
        if (this.channel instanceof FileChannel)
        {
            // all the support is provided by the underlying implementation
            clientFileChannel = (FileChannel) this.channel;
            // debug
            LOGGER.debug("Content reader provided direct support for FileChannel: \n   reader: {}", this);
        }
        else
        {
            // No random access support is provided by the implementation.
            // Spoof it by providing a 2-stage read from a temp file
            final File tempFile = TempFileProvider.createTempFile("random_read_spoof_", ".bin");
            final FileContentWriter spoofWriter = new FileContentWriter(tempFile);
            // pull the content in from the underlying channel
            final FileChannel spoofWriterChannel = spoofWriter.getFileChannel(false);
            try
            {
                final long spoofFileSize = this.getSize();
                spoofWriterChannel.transferFrom(this.channel, 0, spoofFileSize);
            }
            catch (final IOException e)
            {
                throw new ContentIOException("Failed to copy from permanent channel to spoofed temporary channel: \n" + "   reader: "
                        + this + "\n" + "   temp: " + spoofWriter, e);
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
            // get a reader onto the spoofed content
            final ContentReader spoofReader = spoofWriter.getReader();
            // Attach a listener
            // - ensure that the close call gets propogated to the underlying channel
            final ContentStreamListener spoofListener = new ContentStreamListener()
            {

                /**
                 *
                 * {@inheritDoc}
                 */
                @Override
                public void contentStreamClosed() throws ContentIOException
                {
                    try
                    {
                        ContentReaderFacade.this.channel.close();
                    }
                    catch (final IOException e)
                    {
                        throw new ContentIOException("Failed to close underlying channel", e);
                    }
                }
            };
            spoofReader.addListener(spoofListener);
            // we now have the spoofed up channel that the client can work with
            clientFileChannel = spoofReader.getFileChannel();
            // debug
            LOGGER.debug("Content writer provided indirect support for FileChannel: \n   writer: {}\n   temp writer: {}", this, spoofWriter);
        }
        // the file is now available for random access
        return clientFileChannel;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Locale getLocale()
    {
        return this.delegate.getLocale();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLocale(final Locale locale)
    {
        this.delegate.setLocale(locale);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("resource")
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
            throw new ContentIOException("Failed to open stream onto channel: \n   accessor: " + this, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    // overriden with same implementation as AbstractContentReader
    public void getContent(final OutputStream os) throws ContentIOException
    {
        try
        {
            @SuppressWarnings("resource")
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
            @SuppressWarnings("resource")
            final InputStream is = this.getContentInputStream();
            @SuppressWarnings("resource")
            final FileOutputStream os = new FileOutputStream(file);
            FileCopyUtils.copy(is, os); // both streams are closed
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
    @SuppressWarnings("resource")
    // overriden with same implementation as AbstractContentReader
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
            final String content = (encoding == null) ? new String(bytes) : new String(bytes, encoding);
            // done
            return content;
        }
        catch (final Exception e)
        {
            throw new ContentIOException("Failed to copy content to string: \n" + "   accessor: " + this, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    // overriden with same implementation as AbstractContentReader
    public String getContentString(final int length) throws ContentIOException
    {
        if (length < 0 || length > Integer.MAX_VALUE)
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
                reader = new InputStreamReader(this.getContentInputStream());
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
            throw new ContentIOException("Failed to copy content to string: \n" + "   accessor: " + this + "\n" + "   length: " + length, e);
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

    @SuppressWarnings("resource")
    // same base implementation as AbstractContentReader
    protected ReadableByteChannel getCallbackReadableChannel(final ReadableByteChannel directChannel,
            final List<ContentStreamListener> listeners) throws ContentIOException
    {
        ReadableByteChannel callbackChannel = null;
        if (directChannel instanceof FileChannel)
        {
            callbackChannel = this.getCallbackFileChannel((FileChannel) directChannel, listeners);
        }
        else
        {
            // introduce an advistor to handle the callbacks to the listeners
            final ChannelCloseCallbackAdvise advise = new ChannelCloseCallbackAdvise(listeners);
            final ProxyFactory proxyFactory = new ProxyFactory(directChannel);
            proxyFactory.addAdvice(advise);
            callbackChannel = (ReadableByteChannel) proxyFactory.getProxy();
        }
        // done
        LOGGER.debug("Created callback byte channel: \n   original: {}\n   new: {}", directChannel, callbackChannel);
        return callbackChannel;
    }

    // same base implementation as AbstractContentReader
    protected FileChannel getCallbackFileChannel(final FileChannel directChannel, final List<ContentStreamListener> listeners)
            throws ContentIOException
    {
        final FileChannel ret = new CallbackFileChannel(directChannel, listeners);
        // done
        return ret;
    }
}
