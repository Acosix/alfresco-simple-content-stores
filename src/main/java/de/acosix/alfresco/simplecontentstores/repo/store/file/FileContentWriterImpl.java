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
package de.acosix.alfresco.simplecontentstores.repo.store.file;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;

import org.alfresco.repo.content.AbstractContentWriter;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.content.filestore.FileContentStore;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Instances of this class provide the ability to write content to regular files in a file system.
 *
 * This class duplicates {@link org.alfresco.repo.content.filestore.FileContentWriter} simply due to issues with package method visibility
 * that prevent re-use.
 *
 * @author Axel Faust
 */
public class FileContentWriterImpl extends AbstractContentWriter
{

    private static final Logger LOGGER = LoggerFactory.getLogger(FileContentWriterImpl.class);

    protected final File file;

    protected boolean allowRandomAccess;

    /**
     * Constructor that builds a URL based on the absolute path of the file.
     *
     * @param file
     *            the file for writing. This will most likely be directly
     *            related to the content URL.
     */
    public FileContentWriterImpl(final File file)
    {
        this(file, null);
    }

    /**
     * Constructor that builds a URL based on the absolute path of the file.
     *
     * @param file
     *            the file for writing. This will most likely be directly
     *            related to the content URL.
     * @param existingContentReader
     *            a reader of a previous version of this content
     */
    public FileContentWriterImpl(final File file, final ContentReader existingContentReader)
    {
        this(file, FileContentStore.STORE_PROTOCOL + ContentStore.PROTOCOL_DELIMITER + file.getAbsolutePath(), existingContentReader);
    }

    /**
     * Constructor that explicitly sets the URL that the reader represents.
     *
     * @param file
     *            the file for writing. This will most likely be directly
     *            related to the content URL.
     * @param url
     *            the relative url that the reader represents
     * @param existingContentReader
     *            a reader of a previous version of this content
     */
    public FileContentWriterImpl(final File file, final String url, final ContentReader existingContentReader)
    {
        super(url, existingContentReader);

        this.file = file;
        this.allowRandomAccess = true;
    }

    /**
     * @return Returns the file that this writer accesses
     */
    public File getFile()
    {
        return this.file;
    }

    /**
     * @return Returns the size of the underlying file or
     */
    @Override
    public long getSize()
    {
        if (this.file == null)
        {
            return 0L;
        }
        else if (!this.file.exists())
        {
            return 0L;
        }
        else
        {
            return this.file.length();
        }
    }

    /**
     * Sets the enablement flag for random access.
     *
     * @param allow
     *            {@code true} if radnom access should be enabled
     */
    public void setAllowRandomAccess(final boolean allow)
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
    protected WritableByteChannel getDirectWritableChannel() throws ContentIOException
    {
        try
        {
            if (this.file.exists() && this.file.length() > 0)
            {
                throw new IOException("File exists - overwriting not allowed");
            }

            WritableByteChannel channel = null;
            if (this.allowRandomAccess)
            {
                @SuppressWarnings("resource")
                final RandomAccessFile randomAccessFile = new RandomAccessFile(this.file, "rw");  // will create it
                channel = randomAccessFile.getChannel();
            }
            else
            {
                final OutputStream os = new FileOutputStream(this.file);
                channel = Channels.newChannel(os);
            }

            LOGGER.debug("Opened write channel to file: \n\tfile: {}\n\trandom-access: {}", this.file, this.allowRandomAccess);
            return channel;
        }
        catch (final Throwable e)
        {
            throw new ContentIOException("Failed to open file channel: " + this, e);
        }
    }
}
