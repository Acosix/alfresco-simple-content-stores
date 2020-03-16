/*
 * Copyright 2017 - 2020 Acosix GmbH
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
package de.acosix.alfresco.simplecontentstores.repo.store.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import org.alfresco.repo.content.AbstractContentReader;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.content.filestore.FileContentStore;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.FileContentReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Instances of this class provide the ability to read content from regular files in a file system.
 *
 * This class duplicates {@link org.alfresco.repo.content.filestore.FileContentReader} simply due to issues with package method visibility
 * that prevent re-use.
 *
 * @author Axel Faust
 */
public class FileContentReaderImpl extends AbstractContentReader implements FileContentReader
{

    private static final Logger LOGGER = LoggerFactory.getLogger(FileContentReaderImpl.class);

    protected final File file;

    protected boolean allowRandomAccess;

    /**
     * Constructor that builds a URL based on the absolute path of the file.
     *
     * @param file
     *            the file for reading. This will most likely be directly
     *            related to the content URL.
     */
    public FileContentReaderImpl(final File file)
    {
        this(file, FileContentStore.STORE_PROTOCOL + ContentStore.PROTOCOL_DELIMITER + file.getAbsolutePath());
    }

    /**
     * Constructor that explicitely sets the URL that the reader represents.
     *
     * @param file
     *            the file for reading. This will most likely be directly
     *            related to the content URL.
     * @param url
     *            the relative url that the reader represents
     */
    public FileContentReaderImpl(final File file, final String url)
    {
        super(url);

        this.file = file;
        this.allowRandomAccess = true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getFile()
    {
        return this.file;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean exists()
    {
        return this.file.exists();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getSize()
    {
        if (!this.exists())
        {
            return 0L;
        }
        else
        {
            return this.file.length();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getLastModified()
    {
        if (!this.exists())
        {
            return 0L;
        }
        else
        {
            return this.file.lastModified();
        }
    }

    /**
     * Sets the enablement flag for random access.
     *
     * @param allow
     *            {@code true} if radnom access should be enabled
     */
    protected void setAllowRandomAccess(final boolean allow)
    {
        this.allowRandomAccess = allow;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected ContentReader createReader() throws ContentIOException
    {
        /*
         * The URL of the write is known from the start and this method contract states
         * that no consideration needs to be taken w.r.t. the stream state.
         */
        final FileContentReaderImpl reader = new FileContentReaderImpl(this.file, this.getContentUrl());
        reader.setAllowRandomAccess(this.allowRandomAccess);
        return reader;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected ReadableByteChannel getDirectReadableChannel() throws ContentIOException
    {
        try
        {
            if (!this.file.exists())
            {
                throw new IOException("File does not exist: " + this.file);
            }

            ReadableByteChannel channel = null;
            if (this.allowRandomAccess)
            {
                @SuppressWarnings("resource")
                final RandomAccessFile randomAccessFile = new RandomAccessFile(this.file, "r");  // won't create it
                channel = randomAccessFile.getChannel();
            }
            else
            {
                final InputStream is = new FileInputStream(this.file);
                channel = Channels.newChannel(is);
            }

            LOGGER.debug("Opened read channel to file: \n\tfile: {}\n\trandom-access: {}", this.file, this.allowRandomAccess);
            return channel;
        }
        catch (final Throwable e)
        {
            throw new ContentIOException("Failed to open file channel: " + this, e);
        }
    }
}
