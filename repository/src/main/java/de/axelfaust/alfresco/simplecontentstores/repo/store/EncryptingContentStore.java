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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.content.ContentContext;
import org.alfresco.repo.domain.contentdata.ContentDataDAO;
import org.alfresco.repo.domain.contentdata.ContentUrlEntity;
import org.alfresco.repo.domain.contentdata.ContentUrlKeyEntity;
import org.alfresco.repo.domain.contentdata.EncryptedKey;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentStreamListener;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.MimetypeService;
import org.alfresco.service.cmr.repository.MimetypeServiceAware;
import org.alfresco.util.PropertyCheck;
import org.apache.commons.codec.DecoderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;
import org.springframework.util.ResourceUtils;

import com.googlecode.mp4parser.ByteBufferByteChannel;

/**
 * @author Axel Faust
 */
public class EncryptingContentStore extends CommonFacadingContentStore implements ApplicationContextAware, MimetypeServiceAware
{

    private static final Logger LOGGER = LoggerFactory.getLogger(EncryptingContentStore.class);

    private static final String DEFAULT_KEY_ALGORITHM = "AES";

    private static final int DEFAULT_KEY_SIZE = 512;

    protected ApplicationContext applicationContext;

    protected MimetypeService mimetypeService;

    protected ContentDataDAO contentDataDAO;

    protected String keyStorePath;

    protected String keyStoreType = KeyStore.getDefaultType();

    protected String keyStoreProvider;

    protected String keyStorePassword;

    protected String masterKeyAlias;

    protected String masterKeyPassword;

    protected String keyAlgorithm = DEFAULT_KEY_ALGORITHM;

    protected String keyAlgorithmProvider;

    protected int keySize = DEFAULT_KEY_SIZE;

    protected transient Key masterKey;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        super.afterPropertiesSet();

        PropertyCheck.mandatory(this, "mimetypeService", this.mimetypeService);

        PropertyCheck.mandatory(this, "contentDataDAO", this.contentDataDAO);
        PropertyCheck.mandatory(this, "keyStorePath", this.keyStorePath);
        PropertyCheck.mandatory(this, "keyStoreType", this.keyStoreType);
        PropertyCheck.mandatory(this, "masterKeyAlias", this.masterKeyAlias);

        PropertyCheck.mandatory(this, "keyAlgorithm", this.keyAlgorithm);

        this.loadMasterKey();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException
    {
        this.applicationContext = applicationContext;
    }

    /**
     * @param mimetypeService
     *            the mimetypeService to set
     */
    @Override
    public void setMimetypeService(final MimetypeService mimetypeService)
    {
        this.mimetypeService = mimetypeService;
    }

    /**
     * @param contentDataDAO
     *            the contentDataDAO to set
     */
    public void setContentDataDAO(final ContentDataDAO contentDataDAO)
    {
        this.contentDataDAO = contentDataDAO;
    }

    /**
     * @param keyStorePath
     *            the keyStorePath to set
     */
    public void setKeyStorePath(final String keyStorePath)
    {
        this.keyStorePath = keyStorePath;
    }

    /**
     * @param keyStoreType
     *            the keyStoreType to set
     */
    public void setKeyStoreType(final String keyStoreType)
    {
        this.keyStoreType = keyStoreType;
    }

    /**
     * @param keyStoreProvider
     *            the keyStoreProvider to set
     */
    public void setKeyStoreProvider(final String keyStoreProvider)
    {
        this.keyStoreProvider = keyStoreProvider;
    }

    /**
     * @param keyStorePassword
     *            the keyStorePassword to set
     */
    public void setKeyStorePassword(final String keyStorePassword)
    {
        this.keyStorePassword = keyStorePassword;
    }

    /**
     * @param masterKeyAlias
     *            the masterKeyAlias to set
     */
    public void setMasterKeyAlias(final String masterKeyAlias)
    {
        this.masterKeyAlias = masterKeyAlias;
    }

    /**
     * @param masterKeyPassword
     *            the masterKeyPassword to set
     */
    public void setMasterKeyPassword(final String masterKeyPassword)
    {
        this.masterKeyPassword = masterKeyPassword;
    }

    /**
     * @param masterKey
     *            the masterKey to set
     */
    public void setMasterKey(final Key masterKey)
    {
        this.masterKey = masterKey;
    }

    /**
     * @param keyAlgorithm
     *            the keyAlgorithm to set
     */
    public void setKeyAlgorithm(final String keyAlgorithm)
    {
        this.keyAlgorithm = keyAlgorithm;
    }

    /**
     * @param keyAlgorithmProvider
     *            the keyAlgorithmProvider to set
     */
    public void setKeyAlgorithmProvider(final String keyAlgorithmProvider)
    {
        this.keyAlgorithmProvider = keyAlgorithmProvider;
    }

    /**
     * @param keySize
     *            the keySize to set
     */
    public void setKeySize(final int keySize)
    {
        this.keySize = keySize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentReader getReader(final String contentUrl)
    {
        ContentReader reader;
        final ContentReader backingReader = super.getReader(contentUrl);
        if (backingReader != null && backingReader.exists())
        {
            final String effectiveContentUrl = backingReader.getContentUrl();
            final ContentUrlEntity urlEntity = this.contentDataDAO.getContentUrl(effectiveContentUrl);
            if (urlEntity == null)
            {
                throw new ContentIOException("Missing content URL entity for " + effectiveContentUrl);
            }
            final ContentUrlKeyEntity urlKeyEntity = urlEntity.getContentUrlKey();
            if (urlKeyEntity == null)
            {
                throw new ContentIOException("Missing content URL key entity for " + effectiveContentUrl);
            }

            try
            {
                final Key key;
                final EncryptedKey encryptedKey = urlKeyEntity.getEncryptedKey();

                final ByteBuffer ekBuffer = encryptedKey.getByteBuffer();
                try (final ByteBufferByteChannel ekChannel = new ByteBufferByteChannel(ekBuffer))
                {
                    try (final DecryptingReadableByteChannel dkChannel = new DecryptingReadableByteChannel(ekChannel, this.masterKey))
                    {
                        // allocate generously
                        final ByteBuffer dkBuffer = ByteBuffer.allocate(ekBuffer.capacity() * 2);
                        dkChannel.read(dkBuffer);

                        dkBuffer.flip();
                        final byte[] keyBytes = new byte[dkBuffer.remaining()];
                        dkBuffer.get(keyBytes);

                        key = new SecretKeySpec(keyBytes, encryptedKey.getAlgorithm());
                    }
                }

                reader = new DecryptingContentReaderFacade(backingReader, key, urlKeyEntity.getUnencryptedFileSize());
            }
            catch (final DecoderException | IOException e)
            {
                LOGGER.error("Error loading symmetric content encryption key", e);
                throw new ContentIOException("Error loading symmetric content encryption key", e);
            }
        }
        else
        {
            reader = backingReader;
        }
        return reader;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentWriter getWriter(final ContentContext context)
    {
        final ContentWriter backingWriter = super.getWriter(context);
        final ContentReader existingContentReader;

        final String contentUrl = backingWriter.getContentUrl();
        if (this.exists(contentUrl))
        {
            final ContentReader reader = this.getReader(contentUrl);
            if (reader != null && reader.exists())
            {
                existingContentReader = reader;
            }
            else
            {
                existingContentReader = null;
            }
        }
        else
        {
            existingContentReader = null;
        }

        final Key key = this.createNewKey();
        final EncryptingContentWriterFacade facadeWriter = new EncryptingContentWriterFacade(backingWriter, context, key,
                existingContentReader);
        facadeWriter.setMimetypeService(this.mimetypeService);

        facadeWriter.addListener(new ContentStreamListener()
        {

            /**
             *
             * {@inheritDoc}
             */
            @Override
            public void contentStreamClosed() throws ContentIOException
            {
                final byte[] keyBytes = key.getEncoded();
                final ByteBuffer dkBuffer = ByteBuffer.wrap(keyBytes);

                EncryptedKey eKey;
                try
                {
                    final ByteBuffer ekBuffer = ByteBuffer.allocateDirect(dkBuffer.capacity() * 2);
                    try (final ByteBufferByteChannel ekChannel = new ByteBufferByteChannel(ekBuffer))
                    {
                        try (final EncryptingWritableByteChannel dkChannel = new EncryptingWritableByteChannel(ekChannel,
                                EncryptingContentStore.this.masterKey))
                        {
                            dkChannel.write(dkBuffer);
                        }
                    }

                    ekBuffer.flip();

                    eKey = new EncryptedKey(EncryptingContentWriterFacade.class.getSimpleName(), EncryptingContentStore.this.masterKeyAlias,
                            key.getAlgorithm(), ekBuffer);
                }
                catch (final IOException e)
                {
                    LOGGER.error("Error storing symmetric content encryption key", e);
                    throw new ContentIOException("Error storing symmetric content encryption key", e);
                }

                final ContentUrlKeyEntity contentUrlKeyEntity = new ContentUrlKeyEntity();
                contentUrlKeyEntity.setUnencryptedFileSize(Long.valueOf(facadeWriter.getSize()));
                contentUrlKeyEntity.setEncryptedKey(eKey);

                EncryptingContentStore.this.contentDataDAO.updateContentUrlKey(facadeWriter.getContentUrl(), contentUrlKeyEntity);
            }

        });

        return facadeWriter;
    }

    protected void loadMasterKey()
    {
        try
        {
            InputStream keyStoreInput;
            final Resource resource = this.applicationContext.getResource(this.keyStorePath);
            if (resource.exists())
            {
                keyStoreInput = new BufferedInputStream(resource.getInputStream());
            }
            else
            {
                final File keyStoreFile = ResourceUtils.getFile(this.keyStorePath);
                if (keyStoreFile.exists())
                {
                    keyStoreInput = new BufferedInputStream(new FileInputStream(keyStoreFile));
                }
                else
                {
                    keyStoreInput = null;
                }
            }

            if (keyStoreInput == null)
            {
                throw new IllegalStateException("keystore file " + this.keyStorePath + " does not exist / cannot be found");
            }

            final KeyStore keyStore = this.keyStoreProvider != null ? KeyStore.getInstance(this.keyStoreType, this.keyStoreProvider)
                    : KeyStore.getInstance(this.keyStoreType);
            keyStore.load(keyStoreInput, this.keyStorePassword != null ? this.keyStorePassword.toCharArray() : null);

            this.masterKey = keyStore.getKey(this.masterKeyAlias,
                    this.masterKeyPassword != null ? this.masterKeyPassword.toCharArray() : null);
        }
        catch (final NoSuchAlgorithmException | NoSuchProviderException | KeyStoreException | UnrecoverableKeyException | IOException
                | CertificateException e)
        {
            LOGGER.error("Error loading master key from {}", this.keyStorePath, e);
            throw new AlfrescoRuntimeException("Error loading master key", e);
        }
    }

    protected Key createNewKey()
    {
        try
        {
            final KeyGenerator keygen = this.keyAlgorithmProvider != null
                    ? KeyGenerator.getInstance(this.keyAlgorithm, this.keyAlgorithmProvider) : KeyGenerator.getInstance(this.keyAlgorithm);
            keygen.init(this.keySize);
            final SecretKey key = keygen.generateKey();
            return key;
        }
        catch (final NoSuchAlgorithmException | NoSuchProviderException e)
        {
            LOGGER.error("Error generating encryption key", e);
            throw new ContentIOException("Error generating encryption key", e);
        }
    }
}
