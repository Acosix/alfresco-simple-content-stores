package de.acosix.alfresco.simplecontentstores.repo.store.file;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This specialisation of the generic file content writer transparently calculates a digest / checksum of the content written.
 *
 * @author Axel Faust
 */
public class ArchvieFileContentWriterImpl extends FileContentWriterImpl
{

    private static final Logger LOGGER = LoggerFactory.getLogger(ArchvieFileContentWriterImpl.class);

    protected String digestAlgorithm = "SHA-512";

    protected String digestAlgorithmProvider;

    protected byte[] digest;

    /**
     * Constructor that builds a URL based on the absolute path of the file.
     *
     * @param file
     *            the file for writing. This will most likely be directly
     *            related to the content URL.
     */
    public ArchvieFileContentWriterImpl(final File file)
    {
        super(file);
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
    public ArchvieFileContentWriterImpl(final File file, final ContentReader existingContentReader)
    {
        super(file, existingContentReader);
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
    public ArchvieFileContentWriterImpl(final File file, final String url, final ContentReader existingContentReader)
    {
        super(file, url, existingContentReader);
    }

    /**
     * @param digestAlgorithm
     *            the digestAlgorithm to set
     */
    public void setDigestAlgorithm(final String digestAlgorithm)
    {
        this.digestAlgorithm = digestAlgorithm;
    }

    /**
     * @param digestAlgorithmProvider
     *            the digestAlgorithmProvider to set
     */
    public void setDigestAlgorithmProvider(final String digestAlgorithmProvider)
    {
        this.digestAlgorithmProvider = digestAlgorithmProvider;
    }

    /**
     * Retrieves the digest of the content when the content has been fully written. Any attempt to retrieve this before the file channel has
     * been closed will result in a failure.
     *
     * @return the digest bytes
     */
    public byte[] getDigest()
    {
        if (this.digest == null)
        {
            throw new ContentIOException("Content has not yet been written and digest not been calculated");
        }

        final byte[] digest = new byte[this.digest.length];
        System.arraycopy(this.digest, 0, digest, 0, this.digest.length);
        return digest;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected WritableByteChannel getDirectWritableChannel() throws ContentIOException
    {
        final MessageDigest digest;
        try
        {
            if (this.digestAlgorithmProvider != null && this.digestAlgorithmProvider.trim().length() > 0)
            {
                digest = MessageDigest.getInstance(this.digestAlgorithm, this.digestAlgorithmProvider);
            }
            else
            {
                digest = MessageDigest.getInstance(this.digestAlgorithm);
            }
        }
        catch (final NoSuchProviderException nspEx)
        {
            LOGGER.error("Hash algorithm provider {} is not available", this.digestAlgorithmProvider);
            throw new ContentIOException("Hash algorithm provider not available", nspEx);
        }
        catch (final NoSuchAlgorithmException nsaEx)
        {
            LOGGER.error("Hash algorithm {} is not available", this.digestAlgorithm);
            throw new ContentIOException("Hash algorithm not available", nsaEx);
        }

        final WritableByteChannel delegate = super.getDirectWritableChannel();
        final WritableByteChannel writableChannel = new WritableByteChannel()
        {

            /**
             *
             * {@inheritDoc}
             */
            @Override
            public boolean isOpen()
            {
                return delegate.isOpen();
            }

            /**
             *
             * {@inheritDoc}
             */
            @Override
            public void close() throws IOException
            {
                ArchvieFileContentWriterImpl.this.digest = digest.digest();
                delegate.close();
            }

            /**
             *
             * {@inheritDoc}
             */
            @Override
            public int write(final ByteBuffer src) throws IOException
            {
                src.mark();
                digest.update(src);
                src.reset();
                return delegate.write(src);
            }
        };
        return writableChannel;
    }
}
