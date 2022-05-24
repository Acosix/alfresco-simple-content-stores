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

import java.security.Key;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.alfresco.repo.content.ContentContext;
import org.alfresco.repo.domain.contentdata.ContentDataDAO;
import org.alfresco.repo.domain.contentdata.ContentUrlEntity;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.util.PropertyCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.acosix.alfresco.simplecontentstores.repo.store.StoreConstants;
import de.acosix.alfresco.simplecontentstores.repo.store.facade.CommonFacadingContentStore;

/**
 * @author Axel Faust
 */
public class EncryptingContentStore extends CommonFacadingContentStore
{

    private static final Logger LOGGER = LoggerFactory.getLogger(EncryptingContentStore.class);

    protected final String uuid = UUID.randomUUID().toString();

    protected InternalEncryptingContentStoreManager encryptingContentStoreManager;

    protected ContentDataDAO contentDataDAO;

    protected String keyAlgorithm;

    protected int keySize;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        super.afterPropertiesSet();

        PropertyCheck.mandatory(this, "encryptingContentStoreManager", this.encryptingContentStoreManager);
        PropertyCheck.mandatory(this, "contentDataDAO", this.contentDataDAO);

        this.encryptingContentStoreManager.activate();
    }

    /**
     * @param encryptingContentStoreManager
     *     the encryptingContentStoreManager to set
     */
    public void setEncryptingContentStoreManager(final InternalEncryptingContentStoreManager encryptingContentStoreManager)
    {
        this.encryptingContentStoreManager = encryptingContentStoreManager;
    }

    /**
     * @param contentDataDAO
     *     the contentDataDAO to set
     */
    public void setContentDataDAO(final ContentDataDAO contentDataDAO)
    {
        this.contentDataDAO = contentDataDAO;
    }

    /**
     * @param keyAlgorithm
     *     the keyAlgorithm to set
     */
    public void setKeyAlgorithm(final String keyAlgorithm)
    {
        this.keyAlgorithm = keyAlgorithm;
    }

    /**
     * @param keySize
     *     the keySize to set
     */
    public void setKeySize(final int keySize)
    {
        this.keySize = keySize;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public boolean exists(final String contentUrl)
    {
        boolean exists = super.exists(contentUrl);
        if (exists && contentUrl.startsWith(StoreConstants.WILDCARD_PROTOCOL))
        {
            final ContentReader backingReader = super.getReader(contentUrl);
            exists = backingReader != null;
            if (exists)
            {
                // check if backing URL entity actually exists
                // cannot be an existing encrypted content without entity required for associated key
                final String effectiveContentUrl = backingReader.getContentUrl();
                final ContentUrlEntity contentUrlEntity = this.contentDataDAO.getContentUrl(effectiveContentUrl);
                exists = contentUrlEntity != null;
            }
        }
        return exists;
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
            final Optional<SecretKeySpec> decryiptionKey;
            boolean validReader = true;
            if (contentUrl.startsWith(StoreConstants.WILDCARD_PROTOCOL))
            {
                // check if backing URL entity actually exists
                // cannot be an existing encrypted content without entity required for associated key
                final ContentUrlEntity contentUrlEntity = this.contentDataDAO.getContentUrl(effectiveContentUrl);
                if (contentUrlEntity != null)
                {
                    decryiptionKey = this.encryptingContentStoreManager.getDecryiptionKey(effectiveContentUrl);
                }
                else
                {
                    decryiptionKey = Optional.empty();
                    validReader = false;
                }
            }
            else
            {
                decryiptionKey = this.encryptingContentStoreManager.getDecryiptionKey(effectiveContentUrl);
            }

            if (decryiptionKey.isPresent())
            {
                LOGGER.debug("Returning decrypting reader for content URL {}", effectiveContentUrl);

                // unfortunately, in contrast to mimetype / locale / encoding, the size is not set for readers in e.g.
                // ContentServiceImpl.getReader(NodeRef, QName)
                // and even if, it would use the raw file size from content URL entity, not the unencrypted file size from content URL key
                // entity
                final Long fileSize = this.contentDataDAO.getContentUrl(effectiveContentUrl).getContentUrlKey().getUnencryptedFileSize();
                reader = new DecryptingContentReaderFacade(backingReader, decryiptionKey.get(), fileSize);
            }
            else if (validReader)
            {
                LOGGER.debug("Content URL {} has no associated encryption key", effectiveContentUrl);
                reader = backingReader;
            }
            else
            {
                LOGGER.debug("Content URL {} has no associated URL entity", effectiveContentUrl);
                reader = null;
            }
        }
        else
        {
            LOGGER.debug("Content for URL {} does not exist", contentUrl);
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
        final ContentWriter writer;
        if (this.isSpecialHandlingRequired(context))
        {
            LOGGER.debug("Creating encryption enabled writer for context {} in store {}", context, this);
            writer = this.getWriterImpl(context);
        }
        else
        {
            LOGGER.debug("Context {} does not match configured conditions for encryption in store {}", context, this);
            writer = super.getWriter(context);
        }
        return writer;
    }

    protected ContentWriter getWriterImpl(final ContentContext context)
    {
        final ContentReader existingContentReader;

        final String contentUrl = context.getContentUrl();
        if (contentUrl != null && this.isContentUrlSupported(contentUrl) && this.exists(contentUrl))
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

        final ContentWriter backingWriter = super.getWriter(context);

        final Key key = this.keyAlgorithm != null ? this.encryptingContentStoreManager.createEncryptionKey(this.keySize, this.keyAlgorithm)
                : this.encryptingContentStoreManager.createEncryptionKey(this.keySize);
        final EncryptingContentWriterFacade facadeWriter = new EncryptingContentWriterFacade(backingWriter, context, key,
                existingContentReader);

        LOGGER.debug("Created content writer for context {} with (preliminary) content URL {}", context, facadeWriter.getContentUrl());

        facadeWriter.addListener(() -> {
            this.encryptingContentStoreManager.storeEncryptionKey(backingWriter.getContentUrl(), facadeWriter.getUnencryptedSize(),
                    facadeWriter.getEncryptedSize(), key);
        });

        return facadeWriter;
    }
}
