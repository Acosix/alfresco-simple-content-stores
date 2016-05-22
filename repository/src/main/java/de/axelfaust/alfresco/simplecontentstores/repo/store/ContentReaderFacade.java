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

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Locale;

import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentStreamListener;
import org.alfresco.util.ParameterCheck;

/**
 * @author Axel Faust
 */
public class ContentReaderFacade implements ContentReader
{

    protected final ContentReader delegate;

    public ContentReaderFacade(final ContentReader delegate)
    {
        ParameterCheck.mandatory("delegate", delegate);
        this.delegate = delegate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isChannelOpen()
    {
        return this.delegate.isChannelOpen();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addListener(final ContentStreamListener listener)
    {
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
        return this.delegate.isClosed();
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
    public ReadableByteChannel getReadableChannel() throws ContentIOException
    {
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
    @Override
    public FileChannel getFileChannel() throws ContentIOException
    {
        return this.delegate.getFileChannel();
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
    @Override
    public InputStream getContentInputStream() throws ContentIOException
    {
        return this.delegate.getContentInputStream();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void getContent(final OutputStream os) throws ContentIOException
    {
        this.delegate.getContent(os);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void getContent(final File file) throws ContentIOException
    {
        this.delegate.getContent(file);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getContentString() throws ContentIOException
    {
        return this.delegate.getContentString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getContentString(final int length) throws ContentIOException
    {
        return this.delegate.getContentString(length);
    }

}
