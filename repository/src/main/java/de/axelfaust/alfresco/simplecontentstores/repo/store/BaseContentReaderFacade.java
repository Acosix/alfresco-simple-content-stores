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
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;

/**
 * @author Axel Faust
 */
public class BaseContentReaderFacade extends BaseContentAccessorFacade<ContentReader> implements ContentReader
{

    protected BaseContentReaderFacade(final ContentReader delegate)
    {
        super(delegate);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentReader getReader() throws ContentIOException
    {
        return new BaseContentReaderFacade(this.delegate.getReader());
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
    public long getLastModified()
    {
        return this.delegate.getLastModified();
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
    public ReadableByteChannel getReadableChannel() throws ContentIOException
    {
        return this.delegate.getReadableChannel();
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
