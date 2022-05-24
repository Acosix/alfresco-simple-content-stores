/*
 * Copyright 2017 - 2022 Acosix GmbH
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
package de.acosix.alfresco.simplecontentstores.repo.store.encrypted;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.security.GeneralSecurityException;
import java.security.Key;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;

import org.alfresco.service.cmr.repository.ContentIOException;

/**
 *
 * @author Axel Faust
 */
public class DecryptingReadableByteChannel implements ReadableByteChannel
{

    protected final ReadableByteChannel delegateChannel;

    protected final Cipher cipher;

    protected ByteBuffer decryptedInputBuffer;

    protected final ByteBuffer readBuffer = ByteBuffer.allocateDirect(8192);

    protected volatile boolean open = true;

    protected volatile boolean readToEnd = false;

    public DecryptingReadableByteChannel(final ReadableByteChannel delegateChannel, final Key key)
    {
        this.delegateChannel = delegateChannel;

        try
        {
            this.cipher = CipherUtil.getInitialisedCipher(key, false);
        }
        catch (final GeneralSecurityException e)
        {
            throw new ContentIOException("Error initialising cipher", e);
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public boolean isOpen()
    {
        return this.open;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException
    {
        if (!this.open)
        {
            return;
        }

        this.delegateChannel.close();
        this.open = false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int read(final ByteBuffer dst) throws IOException
    {
        int bytesRead;
        if (this.open)
        {
            final int position = dst.position();
            int remaining = dst.remaining();
            if (remaining == 0)
            {
                throw new IOException("Destination buffer has no more available space");
            }

            boolean canReadMore = (this.decryptedInputBuffer != null && this.decryptedInputBuffer.hasRemaining()) || this.doRead() > 0;
            while (remaining > 0 && canReadMore)
            {
                if (remaining < this.decryptedInputBuffer.remaining())
                {
                    final int readLimit = this.decryptedInputBuffer.limit();
                    this.decryptedInputBuffer.limit(this.decryptedInputBuffer.position() + remaining);
                    dst.put(this.decryptedInputBuffer);
                    this.decryptedInputBuffer.limit(readLimit);
                }
                else
                {
                    dst.put(this.decryptedInputBuffer);
                    canReadMore = this.doRead() > 0;
                }
                remaining = dst.remaining();
            }

            bytesRead = dst.position() - position;
            if (bytesRead == 0 && this.readToEnd)
            {
                bytesRead = -1;
            }
        }
        else
        {
            bytesRead = -1;
        }

        return bytesRead;
    }

    protected int doRead() throws IOException
    {
        int effectiveBytesRead = -1;
        if (!this.readToEnd)
        {
            this.readBuffer.clear();

            int bytesRead = 0;
            while (this.readBuffer.hasRemaining() && bytesRead != -1)
            {
                bytesRead = this.delegateChannel.read(this.readBuffer);
            }

            this.readBuffer.flip();
            final int expectedDecryptedSize = this.cipher.getOutputSize(this.readBuffer.limit());

            if (this.decryptedInputBuffer == null || this.decryptedInputBuffer.capacity() < expectedDecryptedSize)
            {
                this.decryptedInputBuffer = ByteBuffer.allocateDirect(expectedDecryptedSize);
            }
            else
            {
                this.decryptedInputBuffer.clear();
                this.decryptedInputBuffer.limit(expectedDecryptedSize);
            }

            try
            {
                this.cipher.update(this.readBuffer, this.decryptedInputBuffer);

                if (bytesRead == -1)
                {
                    final ByteBuffer finalInput = ByteBuffer.allocate(0);
                    this.cipher.doFinal(finalInput, this.decryptedInputBuffer);

                    this.readToEnd = true;
                }
            }
            catch (final BadPaddingException | IllegalBlockSizeException | ShortBufferException e)
            {
                throw new IOException("Unexpected decryption error", e);
            }
            this.decryptedInputBuffer.flip();

            effectiveBytesRead = this.decryptedInputBuffer.limit();
        }

        return effectiveBytesRead;
    }

}