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
import java.nio.channels.WritableByteChannel;

import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.MimetypeService;
import org.alfresco.service.cmr.repository.MimetypeServiceAware;

/**
 * @author Axel Faust
 */
public class BaseContentWriterFacade extends BaseContentAccessorFacade<ContentWriter> implements ContentWriter, MimetypeServiceAware
{

    protected BaseContentWriterFacade(final ContentWriter delegate)
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
    public boolean isClosed()
    {
        return this.delegate.isClosed();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OutputStream getContentOutputStream() throws ContentIOException
    {
        return this.delegate.getContentOutputStream();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putContent(final InputStream is) throws ContentIOException
    {
        this.delegate.putContent(is);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putContent(final File file) throws ContentIOException
    {
        this.delegate.putContent(file);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putContent(final String content) throws ContentIOException
    {
        this.delegate.putContent(content);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putContent(final ContentReader contentReader) throws ContentIOException
    {
        this.delegate.putContent(contentReader);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WritableByteChannel getWritableChannel() throws ContentIOException
    {
        return this.delegate.getWritableChannel();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FileChannel getFileChannel(final boolean truncate) throws ContentIOException
    {
        return this.delegate.getFileChannel(truncate);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void guessMimetype(final String filename)
    {
        this.delegate.guessMimetype(filename);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void guessEncoding()
    {
        this.delegate.guessEncoding();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMimetypeService(final MimetypeService mimetypeService)
    {
        if (this.delegate instanceof MimetypeServiceAware)
        {
            ((MimetypeServiceAware) this.delegate).setMimetypeService(mimetypeService);
        }
    }

}
