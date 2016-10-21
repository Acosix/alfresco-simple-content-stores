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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.WritableByteChannel;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;

import org.alfresco.error.AlfrescoRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Axel Faust, <a href="http://acosix.de">Acosix GmbH</a>
 */
public class EncryptingWritableByteChannel implements WritableByteChannel
{

    private static final Logger LOGGER = LoggerFactory.getLogger(EncryptingWritableByteChannel.class);

    protected final WritableByteChannel delegateChannel;

    protected final Cipher cipher;

    protected volatile boolean open = true;

    protected Collection<EncryptionListener> listeners;

    public EncryptingWritableByteChannel(final WritableByteChannel delegateChannel, final Key key)
    {
        this.delegateChannel = delegateChannel;

        try
        {
            Cipher cipher = Cipher.getInstance(key.getAlgorithm());
            if (cipher.getBlockSize() == 0)
            {
                cipher.init(Cipher.ENCRYPT_MODE, key);
                this.cipher = cipher;
            }
            else
            {
                cipher = Cipher.getInstance(key.getAlgorithm() + "/CBC/PKCS5Padding");

                final byte[] iv = new byte[cipher.getBlockSize()];
                cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
                this.cipher = cipher;
            }
        }
        catch (final NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException | InvalidAlgorithmParameterException e)
        {
            LOGGER.error("Error initializing cipher for key {}", key, e);
            throw new AlfrescoRuntimeException("Error initialising cipher", e);
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public boolean isOpen()
    {
        return this.isOpen();
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException
    {
        if (!this.open)
        {
            return;
        }

        try
        {
            final int expectedOutputSize = this.cipher.getOutputSize(0);
            final ByteBuffer remainingEncryptedOutput = ByteBuffer.allocateDirect(expectedOutputSize);
            this.cipher.doFinal(ByteBuffer.allocate(0), remainingEncryptedOutput);

            remainingEncryptedOutput.flip();
            final int bytesWritten = this.delegateChannel.write(remainingEncryptedOutput);

            if (this.listeners != null)
            {
                for (final EncryptionListener listener : this.listeners)
                {
                    listener.bytesProcessed(0, bytesWritten);
                }
            }
        }
        catch (final BadPaddingException | IllegalBlockSizeException | ShortBufferException e)
        {
            LOGGER.error("Unexepted error during close of encrypting channel", e);
            throw new IOException("Unexpected encryption finalization error", e);
        }
        finally
        {
            // this'll trigger the listeners on the backing channel
            this.delegateChannel.close();
            this.open = false;
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public int write(final ByteBuffer src) throws IOException
    {
        if (!this.open)
        {
            throw new ClosedChannelException();
        }

        final int bytesRead = src.remaining();

        final int expectedOutputSize = this.cipher.getOutputSize(bytesRead);
        final ByteBuffer remainingEncryptedOutput = ByteBuffer.allocateDirect(expectedOutputSize);
        try
        {
            this.cipher.update(src, remainingEncryptedOutput);
        }
        catch (final ShortBufferException e)
        {
            LOGGER.error("Unexepted error during write to encrypting channel", e);
            throw new IOException("Unexpected encryption error", e);
        }

        remainingEncryptedOutput.flip();
        final int bytesWritten = this.delegateChannel.write(remainingEncryptedOutput);

        if (this.listeners != null)
        {
            for (final EncryptionListener listener : this.listeners)
            {
                listener.bytesProcessed(bytesRead, bytesWritten);
            }
        }

        return bytesWritten;
    }

    public void addListener(final EncryptionListener listener)
    {
        if (this.listeners == null)
        {
            this.listeners = new ArrayList<>();
        }
        this.listeners.add(listener);
    }

    /**
     *
     * @author Axel Faust, <a href="http://acosix.de">Acosix GmbH</a>
     */
    public static interface EncryptionListener
    {

        /**
         * Notifies the listener that a specific amount of bytes has been processed. The provided byte counts are deltas and cumulative with
         * any previous notifications.
         *
         * @param bytesRead
         *            the (unencrypted) bytes that have been read
         * @param bytesWritten
         *            the (encrypted) bytes that have been written
         */
        void bytesProcessed(int bytesRead, int bytesWritten);
    }
}