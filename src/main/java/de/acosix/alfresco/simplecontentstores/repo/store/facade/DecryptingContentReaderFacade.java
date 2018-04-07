/*
 * Copyright 2017, 2018 Acosix GmbH
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

import java.nio.channels.ReadableByteChannel;
import java.security.Key;

import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.util.ParameterCheck;

/**
 * @author Axel Faust
 */
public class DecryptingContentReaderFacade extends ContentReaderFacade
{

    protected final Key key;

    protected long unencryptedSize;

    protected DecryptingContentReaderFacade(final ContentReader delegate, final Key key, final long unencryptedSize)
    {
        super(delegate);

        ParameterCheck.mandatory("key", key);
        this.key = key;
        this.unencryptedSize = unencryptedSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getSize()
    {
        return this.unencryptedSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentData getContentData()
    {
        final ContentData contentData = super.getContentData();
        // correct size
        final ContentData updatedData = new ContentData(contentData.getContentUrl(), contentData.getMimetype(), this.unencryptedSize,
                contentData.getEncoding(), contentData.getLocale());
        return updatedData;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentReader getReader() throws ContentIOException
    {
        return new DecryptingContentReaderFacade(super.getReader(), this.key, this.unencryptedSize);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ReadableByteChannel getReadableChannel() throws ContentIOException
    {
        final ReadableByteChannel channel = super.getReadableChannel();
        final ReadableByteChannel eChannel = new DecryptingReadableByteChannel(channel, this.key);
        return eChannel;
    }
}
