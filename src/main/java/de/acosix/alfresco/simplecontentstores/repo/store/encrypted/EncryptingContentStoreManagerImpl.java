/*
 * Copyright 2017 - 2024 Acosix GmbH
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

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;

import org.alfresco.repo.batch.BatchProcessWorkProvider;
import org.alfresco.repo.batch.BatchProcessor;
import org.alfresco.repo.batch.BatchProcessor.BatchProcessWorker;
import org.alfresco.repo.batch.BatchProcessor.BatchProcessWorkerAdaptor;
import org.alfresco.repo.domain.contentdata.ContentDataDAO;
import org.alfresco.repo.domain.contentdata.ContentUrlEntity;
import org.alfresco.repo.domain.contentdata.ContentUrlKeyEntity;
import org.alfresco.repo.domain.contentdata.EncryptedKey;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.Pair;
import org.alfresco.util.ParameterCheck;
import org.alfresco.util.PropertyCheck;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.logging.LogFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import de.acosix.alfresco.simplecontentstores.repo.dao.ContentUrlKeyDAO;

/**
 * @author Axel Faust
 */
public class EncryptingContentStoreManagerImpl implements InternalEncryptingContentStoreManager, InitializingBean
{

    private static final Logger LOGGER = LoggerFactory.getLogger(EncryptingContentStoreManagerImpl.class);

    private static final String DEFAULT_KEY_ALGORITHM = "AES";

    private static final int DEFAULT_KEY_SIZE = 128;

    protected ContentDataDAO contentDataDAO;

    protected ContentUrlKeyDAO contentUrlKeyDAO;

    protected TransactionService transactionService;

    protected InternalMasterKeyManager masterKeyManager;

    protected String defaultKeyAlgorithm = DEFAULT_KEY_ALGORITHM;

    protected int defaultKeySize = DEFAULT_KEY_SIZE;

    protected int reEncryptionThreadCount;

    protected int reEncryptionBatchSize;

    protected int reEncryptionLogInterval;

    /**
     * @param contentDataDAO
     *            the contentDataDAO to set
     */
    public void setContentDataDAO(final ContentDataDAO contentDataDAO)
    {
        this.contentDataDAO = contentDataDAO;
    }

    /**
     * @param contentUrlKeyDAO
     *            the contentUrlKeyDAO to set
     */
    public void setContentUrlKeyDAO(final ContentUrlKeyDAO contentUrlKeyDAO)
    {
        this.contentUrlKeyDAO = contentUrlKeyDAO;
    }

    /**
     * @param transactionService
     *            the transactionService to set
     */
    public void setTransactionService(final TransactionService transactionService)
    {
        this.transactionService = transactionService;
    }

    /**
     * @param masterKeyManager
     *            the masterKeyManager to set
     */
    public void setMasterKeyManager(final InternalMasterKeyManager masterKeyManager)
    {
        this.masterKeyManager = masterKeyManager;
    }

    /**
     * @param defaultKeyAlgorithm
     *            the defaultKeyAlgorithm to set
     */
    public void setDefaultKeyAlgorithm(final String defaultKeyAlgorithm)
    {
        this.defaultKeyAlgorithm = defaultKeyAlgorithm;
    }

    /**
     * @param defaultKeySize
     *            the defaultKeySize to set
     */
    public void setDefaultKeySize(final int defaultKeySize)
    {
        this.defaultKeySize = defaultKeySize;
    }

    /**
     * @param reEncryptionThreadCount
     *            the reEncryptionThreadCount to set
     */
    public void setReEncryptionThreadCount(final int reEncryptionThreadCount)
    {
        this.reEncryptionThreadCount = reEncryptionThreadCount;
    }

    /**
     * @param reEncryptionBatchSize
     *            the reEncryptionBatchSize to set
     */
    public void setReEncryptionBatchSize(final int reEncryptionBatchSize)
    {
        this.reEncryptionBatchSize = reEncryptionBatchSize;
    }

    /**
     * @param reEncryptionLogInterval
     *            the reEncryptionLogInterval to set
     */
    public void setReEncryptionLogInterval(final int reEncryptionLogInterval)
    {
        this.reEncryptionLogInterval = reEncryptionLogInterval;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "contentDataDAO", this.contentDataDAO);
        PropertyCheck.mandatory(this, "contentUrlKeyDAO", this.contentUrlKeyDAO);
        PropertyCheck.mandatory(this, "transactionService", this.transactionService);
        PropertyCheck.mandatory(this, "masterKeyManager", this.masterKeyManager);
        PropertyCheck.mandatory(this, "defaultKeyAlgorithm", this.defaultKeyAlgorithm);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void activate()
    {
        this.masterKeyManager.activate();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<SecretKeySpec> getDecryiptionKey(final String contentUrl)
    {
        ParameterCheck.mandatoryString("contentUrl", contentUrl);

        LOGGER.debug("Checking if content URL {} is associated with an encryption key", contentUrl);

        final ContentUrlEntity urlEntity = this.contentDataDAO.getContentUrl(contentUrl);
        if (urlEntity == null)
        {
            throw new ContentIOException("Missing content URL entity for " + contentUrl);
        }

        Optional<SecretKeySpec> decryptionKey = Optional.empty();

        final ContentUrlKeyEntity urlKeyEntity = urlEntity.getContentUrlKey();
        if (urlKeyEntity != null)
        {
            LOGGER.debug("Content URL {} has an associated encryption key", contentUrl);
            decryptionKey = Optional.of(this.getKeyForKeyEntity(urlKeyEntity));
        }

        return decryptionKey;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Key createEncryptionKey(final int keySize)
    {
        return this.createEncryptionKey(keySize, this.defaultKeyAlgorithm);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Key createEncryptionKey(final String algorithm)
    {
        return this.createEncryptionKey(this.defaultKeySize, algorithm);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Key createEncryptionKey(final int keySize, final String algorithm)
    {
        ParameterCheck.mandatoryString("algorithm", algorithm);

        final int effKeySize = keySize <= 0 ? this.defaultKeySize : keySize;

        LOGGER.debug("Creating new symmetric content encryption key using {} and size of {}", algorithm, effKeySize);
        try
        {
            final KeyGenerator keygen = KeyGenerator.getInstance(algorithm);
            keygen.init(effKeySize);
            return keygen.generateKey();
        }
        catch (final NoSuchAlgorithmException e)
        {
            throw new ContentIOException("Error generating encryption key", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void storeEncryptionKey(final String contentUrl, final long fileSize, final long encryptedFileSize, final Key encryptionKey)
    {
        ParameterCheck.mandatoryString("contentUrl", contentUrl);
        ParameterCheck.mandatory("encryptionKey", encryptionKey);

        LOGGER.debug("Storing symmetric content encryption key for content with URL {} and size of {} bytes after completion of write",
                contentUrl, fileSize);

        final EncryptedKey eKey = this.encryptKey(encryptionKey);

        // note: we could set file size on content URL entity to (proper) encrypted file size by doing getOrCreateContentUrl
        // unfortunately Alfresco takes this value for ContentData construction (no regard for content URL key entity unencrypted file size)
        // and since some code (like result set sorting by size) it needs to be the unencrypted size
        // so we just use updateContentUrlKey (which lazily creates a content URL entity with the unencrypted file size)
        // (one of those cases of "broken by design" in Alfresco)
        this.transactionService.getRetryingTransactionHelper().doInTransaction(() -> {
            final ContentUrlKeyEntity urlKeyEntity = new ContentUrlKeyEntity();
            urlKeyEntity.setEncryptedKey(eKey);
            urlKeyEntity.setUnencryptedFileSize(fileSize);

            final boolean updated = this.contentDataDAO.updateContentUrlKey(contentUrl, urlKeyEntity);
            if (!updated)
            {
                LOGGER.error("Failed to store content encryption key for content URL {}", contentUrl);
                throw new ContentIOException("Failed to link symmetric encryption key with content URL");
            }

            return null;
        }, false, false);

        LOGGER.debug("Stored content encryption key for content URL {}", contentUrl);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reEncryptSymmetricKeys(final MasterKeyReference masterKey)
    {
        ParameterCheck.mandatory("masterKey", masterKey);

        if (this.masterKeyManager.getActiveKeys().contains(masterKey))
        {
            throw new IllegalArgumentException("Specified master key must not be currently active");
        }

        if (!this.masterKeyManager.supportsDecryption(masterKey))
        {
            throw new IllegalArgumentException(
                    "Specified master key is not available for re-encryption of symmetric content encryption keys");
        }

        final BatchProcessWorkProvider<ContentUrlKeyEntity> workProvider = new ReEncryptionWorkProvider(masterKey);
        final BatchProcessWorker<ContentUrlKeyEntity> worker = new ReEncryptionWorker();
        final BatchProcessor<ContentUrlKeyEntity> processor = new BatchProcessor<>("ReEncryptSymmetricKeys",
                this.transactionService.getRetryingTransactionHelper(), workProvider, this.reEncryptionThreadCount,
                this.reEncryptionBatchSize, null, LogFactory.getLog(EncryptingContentStoreManagerImpl.class), this.reEncryptionLogInterval);
        processor.process(worker, true);

        final String lastError = processor.getLastError();
        if (lastError != null)
        {
            LOGGER.warn("Encountered {} errors in re-encryption of symmetric content encryption keys for master key {}",
                    processor.getTotalErrors(), masterKey);
            throw new ContentIOException("Re-encryption of symmetric content encryption keys (partially) failed - "
                    + processor.getTotalErrors() + " errors, last: [" + lastError + "], affecting: " + processor.getLastErrorEntryId());
        }
    }

    protected SecretKeySpec getKeyForKeyEntity(final ContentUrlKeyEntity urlKeyEntity)
    {
        final SecretKeySpec key;
        final EncryptedKey encryptedKey;
        try
        {
            encryptedKey = urlKeyEntity.getEncryptedKey();
        }
        catch (final DecoderException e)
        {
            LOGGER.warn("Failed to load symmetric content encryption key from content URL key entity {}", urlKeyEntity.getId());
            throw new ContentIOException("Error loading symmetric content encryption key", e);
        }

        final Optional<Key> keyDecryptionKey = this.masterKeyManager
                .getDecryptionKey(new MasterKeyReference(encryptedKey.getMasterKeystoreId(), encryptedKey.getMasterKeyAlias()));
        if (!keyDecryptionKey.isPresent())
        {
            LOGGER.warn(
                    "Master key for content URL key entity {} with keystore ID {} and alias {} is not available in configured keystore(s)",
                    urlKeyEntity.getId(), encryptedKey.getMasterKeystoreId(), encryptedKey.getMasterKeyAlias());
            throw new ContentIOException(
                    "Content encryption key was encrypted with an unavailable master key (different key store or alias)");
        }

        final ByteBuffer sourceBuffer = encryptedKey.getByteBuffer();
        try
        {
            final Cipher cipher = CipherUtil.getInitialisedCipher(keyDecryptionKey.get(), false);
            final int targetBufferSize = cipher.getOutputSize(sourceBuffer.remaining());
            final ByteBuffer targetBuffer = ByteBuffer.allocateDirect(targetBufferSize);
            cipher.doFinal(sourceBuffer, targetBuffer);
            targetBuffer.flip();

            final byte[] keyBytes = new byte[targetBuffer.remaining()];
            targetBuffer.get(keyBytes);
            key = new SecretKeySpec(keyBytes, encryptedKey.getAlgorithm());
        }
        catch (final GeneralSecurityException e)
        {
            LOGGER.warn("Failed to decrypt symmetric content encryption key from content URL key entity {}", urlKeyEntity.getId());
            throw new ContentIOException("Error decrypting symmetric content encryption key", e);
        }
        return key;
    }

    protected EncryptedKey encryptKey(final Key encryptionKey)
    {
        EncryptedKey eKey;
        final Pair<MasterKeyReference, Key> keyEncryptionKeyPair = this.masterKeyManager.getRandomActiveEncryptionKey();
        final MasterKeyReference keyEncryptionKeyRef = keyEncryptionKeyPair.getFirst();
        final Key keyEncryptionKey = keyEncryptionKeyPair.getSecond();
        final ByteBuffer sourceBuffer = ByteBuffer.wrap(encryptionKey.getEncoded());
        try
        {
            final Cipher cipher = CipherUtil.getInitialisedCipher(keyEncryptionKey, true);
            final int targetBufferSize = cipher.getOutputSize(sourceBuffer.remaining());
            final ByteBuffer targetBuffer = ByteBuffer.allocateDirect(targetBufferSize);
            cipher.doFinal(sourceBuffer, targetBuffer);
            targetBuffer.flip();

            eKey = new EncryptedKey(keyEncryptionKeyRef.getKeystoreId(), keyEncryptionKeyRef.getAlias(), encryptionKey.getAlgorithm(),
                    targetBuffer);
        }
        catch (final GeneralSecurityException e)
        {
            throw new ContentIOException("Error encrypting symmetric content encryption key", e);
        }
        return eKey;
    }

    /**
     *
     * @author Axel Faust
     */
    protected class ReEncryptionWorkProvider implements BatchProcessWorkProvider<ContentUrlKeyEntity>
    {

        private final MasterKeyReference masterKey;

        private Long lastId;

        protected ReEncryptionWorkProvider(final MasterKeyReference masterKey)
        {
            this.masterKey = masterKey;
        }

        /**
         * Get an estimate of the total number of objects that will be provided by this instance.
         * Instances can provide accurate answers on each call, but only if the answer can be
         * provided quickly and efficiently; usually it is enough to to cache the result after
         * providing an initial estimate.
         *
         * @return a total work size estimate
         */
        public long getTotalEstimatedWorkSizeLong()
        {
            return this.getTotalEstimatedWorkSize();
        }

        /**
         *
         * {@inheritDoc}
         */
        @Override
        public int getTotalEstimatedWorkSize()
        {
            return EncryptingContentStoreManagerImpl.this.transactionService.getRetryingTransactionHelper().doInTransaction(
                    () -> EncryptingContentStoreManagerImpl.this.contentUrlKeyDAO.countSymmetricKeys(this.masterKey), true, false);
        }

        /**
         *
         */
        @Override
        public Collection<ContentUrlKeyEntity> getNextWork()
        {
            final List<ContentUrlKeyEntity> nextWork = EncryptingContentStoreManagerImpl.this.transactionService
                    .getRetryingTransactionHelper().doInTransaction(() -> EncryptingContentStoreManagerImpl.this.contentUrlKeyDAO
                            .getSymmetricKeys(this.masterKey, this.lastId, EncryptingContentStoreManagerImpl.this.reEncryptionBatchSize),
                            true, false);

            if (!nextWork.isEmpty())
            {
                this.lastId = nextWork.get(nextWork.size() - 1).getId();
            }
            return nextWork;
        }

    }

    /**
     *
     * @author Axel Faust
     */
    protected class ReEncryptionWorker extends BatchProcessWorkerAdaptor<ContentUrlKeyEntity>
    {

        /**
         *
         * {@inheritDoc}
         */
        @Override
        public String getIdentifier(final ContentUrlKeyEntity entry)
        {
            return entry.toString();
        }

        /**
         *
         * {@inheritDoc}
         */
        @Override
        public void process(final ContentUrlKeyEntity entry) throws Throwable
        {
            final SecretKeySpec key = EncryptingContentStoreManagerImpl.this.getKeyForKeyEntity(entry);
            final EncryptedKey reEncryptedKey = EncryptingContentStoreManagerImpl.this.encryptKey(key);

            final ContentUrlKeyEntity urlKeyEntity = new ContentUrlKeyEntity();
            // critical: contentDataDAO.updateContentUrlKey does not ensure proper ID is passed to ibatis SQL mapping unless we set it
            urlKeyEntity.setId(entry.getId());
            urlKeyEntity.setEncryptedKey(reEncryptedKey);
            urlKeyEntity.setUnencryptedFileSize(entry.getUnencryptedFileSize());

            final boolean updated = EncryptingContentStoreManagerImpl.this.contentDataDAO.updateContentUrlKey(entry.getContentUrlId(),
                    urlKeyEntity);
            if (!updated)
            {
                LOGGER.info(
                        "Failed to update / store re-encrypted symmetric content encryption key for content URL entity {} - URL or key entity may have been concurrently deleted",
                        entry.getContentUrlId());
            }
        }

    }
}
