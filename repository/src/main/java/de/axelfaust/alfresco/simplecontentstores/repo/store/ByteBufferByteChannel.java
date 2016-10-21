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
import java.nio.channels.ByteChannel;
import java.nio.channels.ClosedChannelException;

import org.alfresco.util.ParameterCheck;

/**
 * This class has been based on {@link com.googlecode.mp4parser.util.ByteBufferByteChannel} to limit the dependency on external / peripheral
 * libraries in case that specific library may be removed / alterered in a different version of Alfresco.
 *
 * @author Axel Faust, <a href="http://acosix.de">Acosix GmbH</a>
 */
public class ByteBufferByteChannel implements ByteChannel
{

    private final ByteBuffer byteBuffer;

    private volatile boolean open = true;

    public ByteBufferByteChannel(final ByteBuffer byteBuffer)
    {
        ParameterCheck.mandatory("byteBuffer", byteBuffer);
        this.byteBuffer = byteBuffer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized int read(final ByteBuffer dst) throws IOException
    {
        int bytesRead = -1;

        if (this.open)
        {
            final int free = dst.remaining();

            if (free > 0)
            {
                if (this.byteBuffer.hasRemaining())
                {
                    if (free < this.byteBuffer.remaining())
                    {
                        final int limit = this.byteBuffer.limit();
                        this.byteBuffer.limit(this.byteBuffer.position() + free);
                        dst.put(this.byteBuffer);
                        this.byteBuffer.limit(limit);
                    }
                    else
                    {
                        dst.put(this.byteBuffer);
                    }
                    bytesRead = free - dst.remaining();
                }
            }
        }

        return bytesRead;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized boolean isOpen()
    {
        return this.open;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void close() throws IOException
    {
        this.open = false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized int write(final ByteBuffer src) throws IOException
    {
        if (!this.open)
        {
            throw new ClosedChannelException();
        }

        final int bytesWritten = src.remaining();
        this.byteBuffer.put(src);
        return bytesWritten;
    }

}
