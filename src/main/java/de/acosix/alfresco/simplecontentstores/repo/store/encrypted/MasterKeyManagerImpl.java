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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import org.alfresco.repo.cache.SimpleCache;
import org.alfresco.service.cmr.attributes.AttributeService;
import org.alfresco.service.cmr.repository.datatype.DefaultTypeConverter;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.EqualsHelper;
import org.alfresco.util.Pair;
import org.alfresco.util.ParameterCheck;
import org.alfresco.util.PropertyCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;
import org.springframework.util.ResourceUtils;

import de.acosix.alfresco.simplecontentstores.repo.dao.ContentUrlKeyDAO;

/**
 * This key manager implementation handles encryption master keys in both single instance and clustered Alfresco installations. Instances of
 * this class are initialised in the following way:
 * <ol>
 * <li>afterPropertiesSet: load any master keys previously disabled at runtime from the data structures of {@link AttributeService} using
 * {@link #ATTR_KEY_DISABLED_MASTER_KEYS the base key for disabled master keys}</li>
 * <li>activate (called indirectly from afterPropertiesSet in an enabled {@link EncryptingContentStore}): {@link #initKeys() loaded
 * encryption master keys} configured for this Alfresco instance</li>
 * <li>onStartup (only if instance has been {@link #activate() activated}: first validate master keys configured for this instance against
 * metadata for master keys loaded from DB (historical usage) and provided from any other active Alfresco cluster instance (if any), then
 * make details about locally configured master keys available in the cluster</li>
 * </ol>
 *
 * Instances of this class make locally configured master key metadata available in the cluster in two forms:
 * <ul>
 * <li>if local instance is first in the cluster: expose metadata of all locally defined master keys</li>
 * <li>otherwise: mark any master keys as blocked from usage in the entire cluster which are either defined by the first instance in the
 * cluster and not available locally, or are available locally but not defined by the first instance in the cluster</li>
 * </ul>
 *
 * @author Axel Faust
 */
public class MasterKeyManagerImpl implements InternalMasterKeyManager, ApplicationContextAware, InitializingBean
{

    private static final Logger LOGGER = LoggerFactory.getLogger(MasterKeyManagerImpl.class);

    private static final String ATTR_KEY_DISABLED_MASTER_KEYS = "acosix/alfresco-simple-content-stores/disabledEncryptionMasterKeys";

    private static final String ATTR_KEY_MASTER_KEY_CHECK_VALUES = "acosix/alfresco-simple-content-stores/encryptionMasterKeyCheckValues";

    private static final SecureRandom RNG = new SecureRandom();

    // lock for the internal, local state (various HashMap instances) - NOT dynamic, potential cluster state in caches
    protected final ReentrantReadWriteLock stateLock = new ReentrantReadWriteLock(true);

    protected final Map<String, Collection<String>> aliasesByKeyStoreId = new HashMap<>();

    protected final Map<MasterKeyReference, String> checkValues = new HashMap<>();

    protected final Map<MasterKeyReference, Key> encryptionKeys = new HashMap<>();

    protected final Map<MasterKeyReference, Key> decryptionKeys = new HashMap<>();

    protected ApplicationContext applicationContext;

    protected ContentUrlKeyDAO contentUrlKeyDAO;

    protected TransactionService transactionService;

    protected AttributeService attributeService;

    protected SimpleCache<MasterKeyReference, String> masterKeyCheckDataCache;

    protected SimpleCache<MasterKeyReference, Boolean> disabledMasterKeyCache;

    protected SimpleCache<MasterKeyReference, Boolean> blockedMasterKeyCache;

    protected Properties properties;

    protected String propertyPrefix;

    protected String keystoreIdsStr;

    protected List<String> keystoreIds;

    protected boolean validated = false;

    protected boolean failMissingDatabaseKeys;

    protected boolean failMissingClusterKeys;

    protected boolean failMismatchedDatabaseKeys;

    protected boolean failMismatchedClusterKeys;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setApplicationContext(final ApplicationContext applicationContext)
    {
        this.applicationContext = applicationContext;
    }

    /**
     * @param contentUrlKeyDAO
     *     the contentUrlKeyDAO to set
     */
    public void setContentUrlKeyDAO(final ContentUrlKeyDAO contentUrlKeyDAO)
    {
        this.contentUrlKeyDAO = contentUrlKeyDAO;
    }

    /**
     * @param transactionService
     *     the transactionService to set
     */
    public void setTransactionService(final TransactionService transactionService)
    {
        this.transactionService = transactionService;
    }

    /**
     * @param attributeService
     *     the attributeService to set
     */
    public void setAttributeService(final AttributeService attributeService)
    {
        this.attributeService = attributeService;
    }

    /**
     * @param masterKeyCheckDataCache
     *     the masterKeyCheckDataCache to set
     */
    public void setMasterKeyCheckDataCache(final SimpleCache<MasterKeyReference, String> masterKeyCheckDataCache)
    {
        this.masterKeyCheckDataCache = masterKeyCheckDataCache;
    }

    /**
     * @param disabledMasterKeyCache
     *     the disabledMasterKeyCache to set
     */
    public void setDisabledMasterKeyCache(final SimpleCache<MasterKeyReference, Boolean> disabledMasterKeyCache)
    {
        this.disabledMasterKeyCache = disabledMasterKeyCache;
    }

    /**
     * @param blockedMasterKeyCache
     *     the blockedMasterKeyCache to set
     */
    public void setBlockedMasterKeyCache(final SimpleCache<MasterKeyReference, Boolean> blockedMasterKeyCache)
    {
        this.blockedMasterKeyCache = blockedMasterKeyCache;
    }

    /**
     * @param properties
     *     the properties to set
     */
    public void setProperties(final Properties properties)
    {
        this.properties = properties;
    }

    /**
     * @param propertyPrefix
     *     the propertyPrefix to set
     */
    public void setPropertyPrefix(final String propertyPrefix)
    {
        this.propertyPrefix = propertyPrefix;
    }

    /**
     * @param keystoreIds
     *     the keystoreIds to set
     */
    public void setKeystoreIds(final String keystoreIds)
    {
        this.keystoreIdsStr = keystoreIds;
    }

    /**
     * @param failMissingDatabaseKeys
     *     the failMissingDatabaseKeys to set
     */
    public void setFailMissingDatabaseKeys(final boolean failMissingDatabaseKeys)
    {
        this.failMissingDatabaseKeys = failMissingDatabaseKeys;
    }

    /**
     * @param failMissingClusterKeys
     *     the failMissingClusterKeys to set
     */
    public void setFailMissingClusterKeys(final boolean failMissingClusterKeys)
    {
        this.failMissingClusterKeys = failMissingClusterKeys;
    }

    /**
     * @param failMismatchedDatabaseKeys
     *     the failMismatchedDatabaseKeys to set
     */
    public void setFailMismatchedDatabaseKeys(final boolean failMismatchedDatabaseKeys)
    {
        this.failMismatchedDatabaseKeys = failMismatchedDatabaseKeys;
    }

    /**
     * @param failMismatchedClusterKeys
     *     the failMismatchedClusterKeys to set
     */
    public void setFailMismatchedClusterKeys(final boolean failMismatchedClusterKeys)
    {
        this.failMismatchedClusterKeys = failMismatchedClusterKeys;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "contentUrlKeyDAO", this.contentUrlKeyDAO);
        PropertyCheck.mandatory(this, "transactionService", this.transactionService);
        PropertyCheck.mandatory(this, "attributeService", this.attributeService);
        PropertyCheck.mandatory(this, "masterKeyCheckDataCache", this.masterKeyCheckDataCache);
        PropertyCheck.mandatory(this, "disabledMasterKeyCache", this.disabledMasterKeyCache);
        PropertyCheck.mandatory(this, "blockedMasterKeyCache", this.blockedMasterKeyCache);
        PropertyCheck.mandatory(this, "properties", this.properties);
        PropertyCheck.mandatory(this, "propertyPrefix", this.propertyPrefix);
        PropertyCheck.mandatory(this, "keystoreIds", this.keystoreIdsStr);

        if (!this.keystoreIdsStr.trim().isEmpty())
        {
            this.keystoreIds = Collections.unmodifiableList(Arrays.asList(this.keystoreIdsStr.trim().split("\\s*,\\s*")));
        }
        else
        {
            this.keystoreIds = Collections.emptyList();
        }

        this.transactionService.getRetryingTransactionHelper().doInTransaction(() -> {
            this.attributeService.getAttributes((id, value, keys) -> {
                if (keys.length == 3 && Boolean.TRUE.equals(value))
                {
                    final String keystoreId = DefaultTypeConverter.INSTANCE.convert(String.class, keys[1]);
                    final String alias = DefaultTypeConverter.INSTANCE.convert(String.class, keys[2]);
                    LOGGER.debug("Marking key with alias {} from key store {} as disabled", alias, keystoreId);
                    this.disabledMasterKeyCache.put(new MasterKeyReference(keystoreId, alias), Boolean.TRUE);
                }
                return true;
            }, ATTR_KEY_DISABLED_MASTER_KEYS);
            return null;
        }, true, false);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public boolean supportsDecryption(final MasterKeyReference masterKey)
    {
        this.stateLock.readLock().lock();
        try
        {
            return this.decryptionKeys.containsKey(masterKey);
        }
        finally
        {
            this.stateLock.readLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<MasterKeyReference> getMismatchedKeys(final boolean current)
    {
        this.stateLock.readLock().lock();
        try
        {
            final Collection<MasterKeyReference> keys;
            if (current)
            {
                keys = this.checkValues.entrySet().stream().filter(e -> this.masterKeyCheckDataCache.contains(e.getKey()))
                        .filter(e -> !EqualsHelper.nullSafeEquals(e.getValue(), this.masterKeyCheckDataCache.get(e.getKey())))
                        .map(Entry::getKey).collect(Collectors.toList());
            }
            else
            {
                keys = this.transactionService.getRetryingTransactionHelper().doInTransaction(() -> {
                    final Map<MasterKeyReference, Integer> usedKeys = this.contentUrlKeyDAO.countSymmetricKeys();
                    return this.checkValues.entrySet().stream().filter(e -> usedKeys.containsKey(e.getKey())).filter(e -> {
                        final MasterKeyReference key = e.getKey();
                        final Serializable usedMasterKeyCheckValue = this.attributeService.getAttribute(ATTR_KEY_MASTER_KEY_CHECK_VALUES,
                                key.getKeystoreId(), key.getAlias());
                        return usedMasterKeyCheckValue != null && !EqualsHelper.nullSafeEquals(e.getValue(), usedMasterKeyCheckValue);
                    }).map(Entry::getKey).collect(Collectors.toList());
                }, true, false);
            }
            return keys;
        }
        finally

        {
            this.stateLock.readLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<MasterKeyReference> getMissingKeys(final boolean current)
    {
        this.stateLock.readLock().lock();
        try
        {
            final Collection<MasterKeyReference> missingKeys;

            if (current)
            {
                // getKeys() is not ideal on a distributed / partitioned cache, but there is no alternative and this operation should only
                // be invoked during startup and via administrative actions
                final Collection<MasterKeyReference> masterKeysInCluster = this.masterKeyCheckDataCache.getKeys();
                missingKeys = masterKeysInCluster.stream().filter(key -> !this.checkValues.containsKey(key)).collect(Collectors.toSet());
            }
            else
            {
                final Map<MasterKeyReference, Integer> usedKeys = this.transactionService.getRetryingTransactionHelper()
                        .doInTransaction(() -> this.contentUrlKeyDAO.countSymmetricKeys(), true, false);
                missingKeys = usedKeys.keySet().stream().filter(key -> !this.checkValues.containsKey(key)).collect(Collectors.toSet());
            }

            return missingKeys;
        }
        finally
        {
            this.stateLock.readLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<MasterKeyReference> getExtraneousKeys()
    {
        this.stateLock.readLock().lock();
        try
        {
            return this.checkValues.keySet().stream().filter(key -> !this.masterKeyCheckDataCache.contains(key))
                    .collect(Collectors.toSet());
        }
        finally
        {
            this.stateLock.readLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<MasterKeyReference> getActiveKeys()
    {
        this.stateLock.readLock().lock();
        try
        {
            return this.encryptionKeys.keySet().stream().filter(key -> !this.blockedMasterKeyCache.contains(key))
                    .filter(key -> !this.disabledMasterKeyCache.contains(key)).collect(Collectors.toList());
        }
        finally
        {
            this.stateLock.readLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<MasterKeyReference> getDisabledKeys()
    {
        this.stateLock.readLock().lock();
        try
        {
            return this.encryptionKeys.keySet().stream().filter(key -> !this.blockedMasterKeyCache.contains(key))
                    .filter(this.disabledMasterKeyCache::contains).collect(Collectors.toSet());
        }
        finally
        {
            this.stateLock.readLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void enable(final MasterKeyReference masterKey)
    {
        ParameterCheck.mandatory("masterKey", masterKey);

        LOGGER.info("Explicit call to enabled key with alias {} from key store {}", masterKey.getAlias(), masterKey.getKeystoreId());

        this.stateLock.readLock().lock();
        try
        {
            if (!this.checkValues.containsKey(masterKey) || !this.masterKeyCheckDataCache.contains(masterKey))
            {
                throw new IllegalArgumentException("Unknown master key: " + masterKey);
            }

            if (!this.getDisabledKeys().contains(masterKey))
            {
                throw new IllegalArgumentException("Master key is not disabled: " + masterKey);
            }

            LOGGER.debug("Removing disablement marker for key with alias {} from key store {} as disabled", masterKey.getAlias(),
                    masterKey.getKeystoreId());
            this.attributeService.removeAttribute(ATTR_KEY_DISABLED_MASTER_KEYS, masterKey.getKeystoreId(), masterKey.getAlias());
            this.disabledMasterKeyCache.remove(masterKey);
        }
        finally
        {
            this.stateLock.readLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void disable(final MasterKeyReference masterKey)
    {
        ParameterCheck.mandatory("masterKey", masterKey);

        LOGGER.info("Explicit call to disable key with alias {} from key store {}", masterKey.getAlias(), masterKey.getKeystoreId());

        this.stateLock.readLock().lock();
        try
        {
            if (!this.checkValues.containsKey(masterKey) || !this.masterKeyCheckDataCache.contains(masterKey))
            {
                throw new IllegalArgumentException("Unknown master key: " + masterKey);
            }

            if (!this.getActiveKeys().contains(masterKey))
            {
                throw new IllegalArgumentException("Master key is not active: " + masterKey);
            }

            final long remainingKeys = this.getActiveKeys().stream().filter(key -> !masterKey.equals(key)).count();
            if (remainingKeys == 0)
            {
                throw new IllegalStateException("Cannot disable last remaining active encryption key");
            }

            LOGGER.debug("Marking key with alias {} from key store {} as disabled", masterKey.getAlias(), masterKey.getKeystoreId());
            this.attributeService.setAttribute(ATTR_KEY_DISABLED_MASTER_KEYS, masterKey.getKeystoreId(), masterKey.getAlias(),
                    Boolean.TRUE);
            this.disabledMasterKeyCache.put(masterKey, Boolean.TRUE);
        }
        finally
        {
            this.stateLock.readLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<MasterKeyReference, Integer> countEncryptedSymmetricKeys()
    {
        return this.transactionService.getRetryingTransactionHelper().doInTransaction(() -> this.contentUrlKeyDAO.countSymmetricKeys(),
                true, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int countEncryptedSymmetricKeys(final MasterKeyReference masterKey)
    {
        return this.transactionService.getRetryingTransactionHelper()
                .doInTransaction(() -> this.contentUrlKeyDAO.countSymmetricKeys(masterKey), true, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<MasterKeyReference> getKeysRequiringReEncryption()
    {
        this.stateLock.readLock().lock();
        try
        {
            final Map<MasterKeyReference, Integer> usedKeys = this.transactionService.getRetryingTransactionHelper()
                    .doInTransaction(() -> this.contentUrlKeyDAO.countSymmetricKeys(), true, false);

            // inactive is not strictly the same as disabled
            // we also include blocked keys which we can still use for decryption of existing symmetric keys
            return this.decryptionKeys.keySet().stream()
                    .filter(key -> this.disabledMasterKeyCache.contains(key) || this.blockedMasterKeyCache.contains(key))
                    .filter(usedKeys::containsKey).collect(Collectors.toSet());
        }
        finally
        {
            this.stateLock.readLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasBeenActivated()
    {
        this.stateLock.readLock().lock();
        try
        {
            return !this.checkValues.isEmpty();
        }
        finally
        {
            this.stateLock.readLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void activate()
    {
        this.stateLock.writeLock().lock();
        try
        {
            if (this.checkValues.isEmpty())
            {
                this.initKeys();
            }
        }
        finally
        {
            this.stateLock.writeLock().unlock();
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void onStartup()
    {
        if (!this.validated)
        {
            try
            {
                if (this.hasBeenActivated())
                {
                    LOGGER.info("Encrypted content stores have been configured - validating settings");

                    final Collection<MasterKeyReference> mismatchedDatabaseKeys = this.getMismatchedKeys(false);
                    if (!mismatchedDatabaseKeys.isEmpty())
                    {
                        if (this.failMismatchedDatabaseKeys)
                        {
                            LOGGER.error(
                                    "Encryption master keys were configured / made available which do not match identically named keys recorded in the database - the following keys are affected: {}",
                                    mismatchedDatabaseKeys);
                            throw new IllegalStateException("Encryption master keys with mismatches to database-recorded keys found");
                        }
                        LOGGER.warn(
                                "Encryption master keys were configured / made available which do not match identically named keys recorded in the database - the following keys are affected: {}",
                                mismatchedDatabaseKeys);
                    }

                    final Collection<MasterKeyReference> mismatchedClusterKeys = this.getMismatchedKeys(true);
                    if (!mismatchedClusterKeys.isEmpty())
                    {
                        if (this.failMismatchedClusterKeys)
                        {
                            LOGGER.error(
                                    "Encryption master keys were configured / made available which do not match identically named keys on other cluster servers - the following keys are affected: {}",
                                    mismatchedClusterKeys);
                            throw new IllegalStateException("Encryption master keys with mismatches to other cluster servers found");
                        }
                        LOGGER.warn(
                                "Encryption master keys were configured / made available which do not match identically named keys on other cluster servers - the following keys are affected: {}",
                                mismatchedClusterKeys);
                    }

                    final Collection<MasterKeyReference> missingDatabaseKeys = this.getMissingKeys(false);
                    if (!missingDatabaseKeys.isEmpty())
                    {
                        if (this.failMissingDatabaseKeys)
                        {
                            LOGGER.error(
                                    "Encryption master keys previously recorded in the database were not configured / made available - the following keys are affected: {}",
                                    missingDatabaseKeys);
                            throw new IllegalStateException("Missing database-recorded encryption master keys found");
                        }
                        LOGGER.warn(
                                "Encryption master keys previously recorded in the database were not configured / made available - the following keys will not be used: {}",
                                missingDatabaseKeys);
                    }

                    final Collection<MasterKeyReference> missingClusterKeys = this.getMissingKeys(true);
                    if (!missingClusterKeys.isEmpty())
                    {
                        if (this.failMissingClusterKeys)
                        {
                            LOGGER.error(
                                    "Encryption master keys available on other cluster servers were not configured / made available - the following keys are affected: {}",
                                    missingClusterKeys);
                            throw new IllegalStateException("Missing encryption master keys compared to other cluster servers found");
                        }
                        LOGGER.warn(
                                "Encryption master keys available on other cluster servers were not configured / made available - the following keys will be marked as blocked: {}",
                                missingClusterKeys);
                    }

                    this.makeKeyInformationAvailableInCluster();

                    final Collection<MasterKeyReference> extraneousKeys = this.getExtraneousKeys();
                    if (!extraneousKeys.isEmpty())
                    {
                        LOGGER.warn(
                                "More encryption master keys were configured / made available than on other servers in the same cluster - the following keys will be marked as blocked: {}",
                                extraneousKeys);
                    }

                    final Collection<MasterKeyReference> activeKeys = this.getActiveKeys();
                    if (activeKeys.isEmpty())
                    {
                        throw new IllegalStateException("No usable encryption master keys are available");
                    }
                }
            }
            finally
            {
                this.validated = true;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Pair<MasterKeyReference, Key> getRandomActiveEncryptionKey()
    {
        this.stateLock.readLock().lock();
        try
        {
            final List<MasterKeyReference> activeKeys = this.getActiveKeys();
            if (activeKeys.isEmpty())
            {
                throw new IllegalStateException("Master key manager has not been activated / initialised");
            }
            final MasterKeyReference selectedKey = activeKeys.get(RNG.nextInt(activeKeys.size()));

            LOGGER.debug("Randomly picked key with alias {} from key store {}", selectedKey.getAlias(), selectedKey.getKeystoreId());

            this.transactionService.getRetryingTransactionHelper().doInTransaction(() -> {
                final Serializable usedMasterKeyCheckValue = this.attributeService.getAttribute(ATTR_KEY_MASTER_KEY_CHECK_VALUES,
                        selectedKey.getKeystoreId(), selectedKey.getAlias());
                final String checkValue = this.checkValues.get(selectedKey);
                if (!EqualsHelper.nullSafeEquals(usedMasterKeyCheckValue, checkValue))
                {
                    this.attributeService.setAttribute(checkValue, ATTR_KEY_MASTER_KEY_CHECK_VALUES, selectedKey.getKeystoreId(),
                            selectedKey.getAlias());
                }
                return null;
            }, false, false);

            return new Pair<>(selectedKey, this.encryptionKeys.get(selectedKey));
        }
        finally
        {
            this.stateLock.readLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Key> getDecryptionKey(final MasterKeyReference masterKey)
    {
        ParameterCheck.mandatory("masterKey", masterKey);
        this.stateLock.readLock().lock();
        try
        {
            final Key decryptionKey = this.decryptionKeys.get(masterKey);
            return Optional.ofNullable(decryptionKey);
        }
        finally
        {
            this.stateLock.readLock().unlock();
        }
    }

    /**
     * Loads the contents of {@link #keystoreIds configured keystores}, initialising the set of available master keys. This operation must
     * be called within {@link #stateLock an active write lock}.
     */
    protected void initKeys()
    {
        PropertyCheck.mandatory(this, "keystoreIds", this.keystoreIds);
        if (this.keystoreIds.isEmpty())
        {
            throw new IllegalStateException("No keystore IDs have been configured");
        }

        for (final String keystoreId : this.keystoreIds)
        {
            this.loadKeysFromStore(keystoreId);
        }
    }

    protected void loadKeysFromStore(final String keystoreId)
    {
        LOGGER.debug("Looking up configuration properties for keystore {}", keystoreId);

        final String keystoreBaseProperty = this.propertyPrefix + keystoreId;
        final String locationProperty = keystoreBaseProperty + ".location";
        final String typeProperty = keystoreBaseProperty + ".type";
        final String providerProperty = keystoreBaseProperty + ".provider";
        final String aliasProperty = keystoreBaseProperty + ".aliases";

        String keystoreLocation = this.properties.getProperty(locationProperty);
        String keystoreType = this.properties.getProperty(typeProperty);
        String keystoreProvider = this.properties.getProperty(providerProperty);
        String keystoreAliases = this.properties.getProperty(aliasProperty);

        if (keystoreLocation == null || keystoreLocation.trim().isEmpty())
        {
            keystoreLocation = System.getProperty(locationProperty);
        }
        if (keystoreType == null || keystoreType.trim().isEmpty())
        {
            keystoreType = System.getProperty(typeProperty);
        }
        if (keystoreProvider == null || keystoreProvider.trim().isEmpty())
        {
            keystoreProvider = System.getProperty(providerProperty);
        }
        if (keystoreAliases == null || keystoreAliases.trim().isEmpty())
        {
            keystoreAliases = System.getProperty(aliasProperty);
        }

        ParameterCheck.mandatoryString(locationProperty, keystoreLocation);
        ParameterCheck.mandatoryString(typeProperty, keystoreType);
        ParameterCheck.mandatoryString(aliasProperty, keystoreAliases);

        InputStream keyStoreInput = null;
        try
        {
            keyStoreInput = this.getStream(keystoreLocation);

            if (keyStoreInput == null)
            {
                throw new IllegalStateException("Keystore file " + keystoreLocation + " does not exist / cannot be found");
            }

            final KeyStore keyStore = keystoreProvider != null && !keystoreProvider.isEmpty()
                    ? KeyStore.getInstance(keystoreType, keystoreProvider)
                    : KeyStore.getInstance(keystoreType);
            final String storePassword = this.getPassword(keystoreBaseProperty + ".password");
            keyStore.load(keyStoreInput, storePassword != null ? storePassword.toCharArray() : null);

            for (final String alias : keystoreAliases.trim().split("\\s*,\\s*"))
            {
                this.loadKey(keystoreId, keyStore, alias);
            }
        }
        catch (final IOException | NoSuchAlgorithmException | NoSuchProviderException | CertificateException | KeyStoreException e)
        {
            throw new IllegalStateException("Failed to load master keystore", e);
        }
        finally
        {
            if (keyStoreInput != null)
            {
                try
                {
                    keyStoreInput.close();
                }
                catch (final IOException ignore)
                {
                    // NO-OP
                }
            }
        }
    }

    /**
     * Loads an individual key from a master keystore. This operation must be called within {@link #stateLock an active write lock}.
     *
     * @param keystoreId
     *     the ID of the keystore
     * @param keystore
     *     the master keystore
     * @param alias
     *     the alias of the key to load
     */
    protected void loadKey(final String keystoreId, final KeyStore keystore, final String alias)
    {
        LOGGER.debug("Loading key with alias {} from key store {}", alias, keystoreId);

        final String keystoreBaseProperty = this.propertyPrefix + keystoreId;
        final String keyPassword = this.getPassword(keystoreBaseProperty + "." + alias + ".password");

        try
        {
            final MasterKeyReference keyRef = new MasterKeyReference(keystoreId, alias);
            final Certificate cert = keystore.getCertificate(alias);
            final Key key = keystore.getKey(alias, keyPassword != null ? keyPassword.toCharArray() : null);

            if (cert == null)
            {
                if (key instanceof PrivateKey)
                {
                    LOGGER.warn(
                            "Only private key found in key store {} for alias {} - use will be limited to decrypting symmetric encryption keys",
                            keystoreId, alias);

                    this.decryptionKeys.put(keyRef, key);

                    this.checkValues.put(keyRef, key.getAlgorithm() + '#' + Arrays.hashCode(key.getEncoded()));
                }
                else if (key != null)
                {
                    LOGGER.debug("Symmetric encryption key found in key store {} for alias {}", keystoreId, alias);

                    this.encryptionKeys.put(keyRef, key);
                    this.decryptionKeys.put(keyRef, key);

                    this.checkValues.put(keyRef, key.getAlgorithm() + '#' + Arrays.hashCode(key.getEncoded()));
                }
                else
                {
                    LOGGER.warn("No certificate w/ public key or symmetric/private key found in key store {} for alias {}", keystoreId,
                            alias);
                }
            }
            else if (key instanceof PrivateKey)
            {
                LOGGER.debug("Asymmetric encryption keys found in key store {} for alias {}", keystoreId, alias);

                this.encryptionKeys.put(keyRef, cert.getPublicKey());
                this.decryptionKeys.put(keyRef, key);

                this.checkValues.put(keyRef, key.getAlgorithm() + '#' + Arrays.hashCode(key.getEncoded()));
            }
            else
            {
                LOGGER.warn("No private key found in key store {} for alias {} to match certificate w/ public", keystoreId, alias);
            }
        }
        catch (final NoSuchAlgorithmException | KeyStoreException | UnrecoverableKeyException e)
        {
            LOGGER.error("Failed to load details of alias {}", alias, e);
        }
    }

    protected String getPassword(final String propertyKey)
    {
        final String locationPropertyKey = propertyKey + ".location";
        String locationProperty = this.properties.getProperty(locationPropertyKey);
        if (locationProperty == null || locationProperty.trim().isEmpty())
        {
            locationProperty = System.getProperty(locationPropertyKey);
        }
        if (locationProperty != null)
        {
            locationProperty = locationProperty.trim();
        }

        return this.getPassword(propertyKey, locationProperty);
    }

    protected String getPassword(final String propertyKey, final String location)
    {
        String password = this.readPasswordFromFile(location);

        if (password == null || password.isEmpty())
        {
            password = this.properties.getProperty(propertyKey);
        }

        if (password == null || password.isEmpty())
        {
            password = System.getProperty(propertyKey);
        }

        return password;
    }

    protected String readPasswordFromFile(final String location)
    {
        String password = null;
        if (location != null && !location.trim().isEmpty())
        {
            BufferedReader reader = null;
            try
            {
                final InputStream is = this.getStream(location);
                if (is != null)
                {
                    reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                    password = reader.readLine();
                }
            }
            catch (final IOException ioex)
            {
                LOGGER.warn("Failed to read password from {}", location, ioex);
            }
            finally
            {
                if (reader != null)
                {
                    try
                    {
                        reader.close();
                    }
                    catch (final IOException ignore)
                    {
                        // NO-OP
                    }
                }
            }
        }
        return password;
    }

    @SuppressWarnings("resource")
    protected InputStream getStream(final String path) throws IOException
    {
        InputStream is = null;
        final Resource resource = this.applicationContext.getResource(path);
        if (resource.exists())
        {
            is = resource.getInputStream();
        }
        else
        {
            final File keyStoreFile = ResourceUtils.getFile(path);
            if (keyStoreFile.exists())
            {
                is = new FileInputStream(keyStoreFile);
            }
        }
        return is;
    }

    protected void makeKeyInformationAvailableInCluster()
    {
        this.stateLock.readLock().lock();
        try
        {
            LOGGER.info("Pushing key information to Alfresco caches");

            final boolean initialData = this.masterKeyCheckDataCache.getKeys().isEmpty();
            if (initialData)
            {
                for (final Entry<MasterKeyReference, String> checkEntry : this.checkValues.entrySet())
                {
                    this.masterKeyCheckDataCache.put(checkEntry.getKey(), checkEntry.getValue());
                }
            }
            else
            {
                // block missing + extraneous keys
                this.masterKeyCheckDataCache.getKeys().stream().filter(key -> !this.checkValues.containsKey(key))
                        .forEach(key -> this.blockedMasterKeyCache.put(key, Boolean.TRUE));
                this.checkValues.keySet().stream().filter(key -> !this.masterKeyCheckDataCache.contains(key))
                        .forEach(key -> this.blockedMasterKeyCache.put(key, Boolean.TRUE));
            }
        }
        finally
        {
            this.stateLock.readLock().unlock();
        }
    }
}
