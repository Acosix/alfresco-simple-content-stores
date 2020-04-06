package de.acosix.alfresco.simplecontentstores.repo.store.file;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.alfresco.repo.node.NodeServicePolicies.OnUpdatePropertiesPolicy;
import org.alfresco.repo.policy.Behaviour.NotificationFrequency;
import org.alfresco.repo.policy.JavaBehaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.repo.transaction.TransactionalResourceHelper;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.dictionary.PropertyDefinition;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.datatype.DefaultTypeConverter;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.EqualsHelper;
import org.alfresco.util.PropertyCheck;
import org.alfresco.util.transaction.TransactionListener;
import org.alfresco.util.transaction.TransactionSupportUtil;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.acosix.alfresco.simplecontentstores.repo.store.ContentUrlUtils;
import de.acosix.alfresco.simplecontentstores.repo.store.context.ContentStoreContext;

/**
 * Instances of this specialised file content store handle advanced requirements of document archival in regular file system-based
 * locations. This includes generation of file hash checksums during file writing, setting the file's read-only flag before the transaction
 * commits, as well as setting potential retention target property values as archive file times which may be picked up by hardware solutions
 * to lock the files until the specified date. Generated checksums are available for {@link #getContentUrlHashes() retrieval} by any
 * behaviours that wish to persist this information on the affected node.
 *
 * @author Axel Faust
 */
public class ArchiveFileContentStore extends FileContentStore implements TransactionListener, OnUpdatePropertiesPolicy
{

    private static final Logger LOGGER = LoggerFactory.getLogger(ArchiveFileContentStore.class);

    // copied from AlfrescoTransactionSupport as the most appropriate listener priority and due to accessibility restrictions
    private static int COMMIT_ORDER_DAO = 3;

    private static final String TXN_CONTENT_URL_HASHES = ArchiveFileContentStore.class.getName() + "-contentUrlHashes";

    private static final String TXN_WRITTEN_FILES = ArchiveFileContentStore.class.getName() + "-writtenFiles";

    /**
     * Retrieves the content hashes for all content URLs created by instances of this class in the current transaction.
     *
     * @return the content hashes keyed by the content URL - the content hashes are all prefixed by the algorithm used to derive the hash,
     *         e.g. {@code sha-512:...} with {@link #setDigestAlgorithm(String) algorithm names} always in lower-cased form
     */
    public static Map<String, String> getContentUrlHashes()
    {
        Map<String, String> map = TransactionalResourceHelper.getMap(TXN_CONTENT_URL_HASHES);
        // decouple
        map = new HashMap<>(map);
        return map;
    }

    protected PolicyComponent policyComponent;

    protected NamespaceService namespaceService;

    protected DictionaryService dictionaryService;

    protected NodeService nodeService;

    protected boolean retentionViaAccessTime;

    protected boolean prolongRetentionOnPropertyChange;

    protected String retentionDatePropertyName;

    protected QName retentionDatePropertyQName;

    protected String digestAlgorithm = "SHA-512";

    protected String digestAlgorithmProvider;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        super.afterPropertiesSet();

        // if we ever support more variants, OR this
        if (this.retentionViaAccessTime)
        {
            PropertyCheck.mandatory(this, "namespaceService", this.namespaceService);
            PropertyCheck.mandatory(this, "dictionaryService", this.dictionaryService);
            PropertyCheck.mandatory(this, "nodeService", this.nodeService);
            PropertyCheck.mandatory(this, "retentionDatePropertyName", this.retentionDatePropertyName);

            this.retentionDatePropertyQName = QName.resolveToQName(this.namespaceService, this.retentionDatePropertyName);
            if (this.retentionDatePropertyQName == null)
            {
                throw new IllegalStateException(this.retentionDatePropertyName + " cannot be resolved to a qualified name");
            }

            final PropertyDefinition property = this.dictionaryService.getProperty(this.retentionDatePropertyQName);
            if (property == null || !(DataTypeDefinition.DATE.equals(property.getDataType().getName())
                    || DataTypeDefinition.DATETIME.equals(property.getDataType().getName())))
            {
                throw new IllegalStateException(
                        this.retentionDatePropertyName + " is not a date/datetime property defined in the data model");
            }

            if (this.prolongRetentionOnPropertyChange)
            {
                PropertyCheck.mandatory(this, "policyComponent", this.policyComponent);

                this.policyComponent.bindClassBehaviour(OnUpdatePropertiesPolicy.QNAME, property.getContainerClass().getName(),
                        new JavaBehaviour(this, "onUpdateProperties", NotificationFrequency.EVERY_EVENT));
            }
        }

        PropertyCheck.mandatory(this, "digestAlgorithm", this.digestAlgorithm);
    }

    /**
     * @param policyComponent
     *            the policyComponent to set
     */
    public void setPolicyComponent(final PolicyComponent policyComponent)
    {
        this.policyComponent = policyComponent;
    }

    /**
     * @param namespaceService
     *            the namespaceService to set
     */
    public void setNamespaceService(final NamespaceService namespaceService)
    {
        this.namespaceService = namespaceService;
    }

    /**
     * @param dictionaryService
     *            the dictionaryService to set
     */
    public void setDictionaryService(final DictionaryService dictionaryService)
    {
        this.dictionaryService = dictionaryService;
    }

    /**
     * @param nodeService
     *            the nodeService to set
     */
    public void setNodeService(final NodeService nodeService)
    {
        this.nodeService = nodeService;
    }

    /**
     * @param retentionViaAccessTime
     *            the retentionViaAccessTime to set
     */
    public void setRetentionViaAccessTime(final boolean retentionViaAccessTime)
    {
        this.retentionViaAccessTime = retentionViaAccessTime;
    }

    /**
     * @param prolongRetentionOnPropertyChange
     *            the prolongRetentionOnPropertyChange to set
     */
    public void setProlongRetentionOnPropertyChange(final boolean prolongRetentionOnPropertyChange)
    {
        this.prolongRetentionOnPropertyChange = prolongRetentionOnPropertyChange;
    }

    /**
     * @param retentionDatePropertyName
     *            the retentionDatePropertyName to set
     */
    public void setRetentionDatePropertyName(final String retentionDatePropertyName)
    {
        this.retentionDatePropertyName = retentionDatePropertyName;
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
     * {@inheritDoc}
     */
    @Override
    public void beforeCommit(final boolean readOnly)
    {
        final List<File> files = TransactionalResourceHelper.getList(TXN_WRITTEN_FILES);
        files.stream().map(File::toPath).forEach(p -> {
            try
            {
                final Set<PosixFilePermission> permissions = EnumSet.copyOf(Files.getPosixFilePermissions(p));
                permissions.remove(PosixFilePermission.OWNER_WRITE);
                permissions.remove(PosixFilePermission.GROUP_WRITE);
                permissions.remove(PosixFilePermission.OTHERS_WRITE);
                Files.setPosixFilePermissions(p, permissions);
            }
            catch (final UnsupportedOperationException ex)
            {
                LOGGER.debug(
                        "File system does not support posix file attributes - unable to remove granular write permissions and falling back to legacy API to set readOnly file flag");
                if (!p.toFile().setReadOnly())
                {
                    throw new ContentIOException("Failed to remove write permissions on " + p);
                }
            }
            catch (final IOException ioex)
            {
                throw new ContentIOException("Failed to remove write permissions on " + p, ioex);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeCompletion()
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterCommit()
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterRollback()
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onUpdateProperties(final NodeRef nodeRef, final Map<QName, Serializable> before, final Map<QName, Serializable> after)
    {
        // no need to check if retentionDatePropertyQName or retentionViaAccessTime are actually set
        // behaviour would not have been registered if that wasn't the case

        final Serializable retentionBefore = before.get(this.retentionDatePropertyQName);
        final Serializable retentionAfter = after.get(this.retentionDatePropertyQName);

        if (!EqualsHelper.nullSafeEquals(retentionBefore, retentionAfter))
        {
            LOGGER.debug("Handling update of retention target {} on {} from {} to {}", this.retentionDatePropertyQName, nodeRef,
                    retentionBefore, retentionAfter);
            final Date retentionTargetDate = DefaultTypeConverter.INSTANCE.convert(Date.class, retentionAfter);
            if (retentionTargetDate != null)
            {
                final Map<QName, Serializable> properties = this.nodeService.getProperties(nodeRef);

                final List<QName> contentPropertyQNames = properties.keySet().stream().map(this.dictionaryService::getProperty)
                        .filter(p -> p != null).filter(p -> DataTypeDefinition.CONTENT.equals(p.getDataType().getName()))
                        .map(PropertyDefinition::getName).collect(Collectors.toList());
                LOGGER.debug("Identified {} as d:content properties to process on {}", contentPropertyQNames, nodeRef);

                contentPropertyQNames.stream().map(properties::get)
                        .forEach(pv -> this.processContentPropertyForRetentionUpdate(pv, retentionTargetDate, retentionBefore == null));
            }
            else
            {
                LOGGER.warn("Unable to update retention on any content of {} as node no longer has a value for {}", nodeRef,
                        this.retentionDatePropertyQName);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ContentWriter getWriterInternal(final ContentReader existingContentReader, final String newContentUrl)
    {
        String contentUrl = null;
        try
        {
            if (newContentUrl == null)
            {
                contentUrl = this.createNewFileStoreUrl();
            }
            else
            {
                contentUrl = ContentUrlUtils.checkAndReplaceWildcardProtocol(newContentUrl, this.protocol);
            }

            final File file = this.createNewFile(contentUrl);
            final ArchvieFileContentWriterImpl writer = new ArchvieFileContentWriterImpl(file, contentUrl, existingContentReader);

            if (this.contentLimitProvider != null)
            {
                writer.setContentLimitProvider(this.contentLimitProvider);
            }

            writer.setAllowRandomAccess(this.allowRandomAccess);
            writer.setDigestAlgorithm(this.digestAlgorithm);
            writer.setDigestAlgorithmProvider(this.digestAlgorithmProvider);

            final NodeRef contextNodeRef = (NodeRef) ContentStoreContext.getContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_NODE);

            writer.addListener(() -> {
                this.txnStoreHash(writer);
                this.txnStoreFile(file);

                this.checkAndSetRetention(file, contextNodeRef);
            });

            LOGGER.debug("Created content writer: \n   writer: {}", writer);
            return writer;
        }
        catch (final Throwable e)
        {
            LOGGER.error("Error creating writer for {}", contentUrl, e);
            throw new ContentIOException("Failed to get writer for URL: " + contentUrl, e);
        }
    }

    /**
     * Stores the hash of a written content file in the transaction for potential clients to pick up.
     *
     * @param writer
     *            the writer used to write the content file
     */
    protected void txnStoreHash(final ArchvieFileContentWriterImpl writer)
    {
        final Map<String, String> map = TransactionalResourceHelper.getMap(TXN_CONTENT_URL_HASHES);
        final byte[] digest = writer.getDigest();
        final char[] digestCh = Hex.encodeHex(digest, false);
        final String digestStr = new String(digestCh);
        final String digestVal = this.digestAlgorithm.toLowerCase(Locale.ENGLISH) + ':' + digestStr;
        map.put(writer.getContentUrl(), digestVal);
    }

    /**
     * Stores the handle of a written content file in the transaction for later enablement of the read-only flag before the transaction
     * concludes but after all integrity checks have been performed.
     *
     * @param file
     *            the handle of the written content file
     */
    protected void txnStoreFile(final File file)
    {
        final List<File> files = TransactionalResourceHelper.getList(TXN_WRITTEN_FILES);
        if (files.isEmpty())
        {
            // may cause ConcurrentModificationException when already in beforeCommit and this priority is being processed
            // there is no way to guard against it unfortunately
            // reasonably, no call should be expected during DAO priority phase
            TransactionSupportUtil.bindListener(this, COMMIT_ORDER_DAO);
        }
        files.add(file);
    }

    /**
     * Checks whether a retention time can be set for a freshly written content file and sets it accordingly, if possible.
     *
     * @param file
     *            the handle of the written content file
     * @param nodeRef
     *            the context node referencing the written content
     */
    protected void checkAndSetRetention(final File file, final NodeRef nodeRef)
    {
        // if we ever support more variants, OR this
        if (this.retentionViaAccessTime)
        {
            if (nodeRef != null)
            {
                final Date retentionTargetDate = DefaultTypeConverter.INSTANCE.convert(Date.class,
                        this.nodeService.getProperty(nodeRef, this.retentionDatePropertyQName));
                if (retentionTargetDate != null)
                {
                    if (this.retentionViaAccessTime)
                    {
                        this.setRetentionViaAccessTime(file, retentionTargetDate, true);
                    }
                }
                else
                {
                    LOGGER.warn("Unable to set retention on {} as node {} does not have a value for {}", file, nodeRef,
                            this.retentionDatePropertyQName);
                }
            }
            else
            {
                LOGGER.warn("Unable to set retention on {} as no node was provided as context for the write operation", file);
            }
        }
    }

    /**
     * Processes the update of a retention target on a specific node content property value.
     *
     * @param propertyValue
     *            the property value holding one or more content data details
     * @param retentionTargetDate
     *            the new retention target date value
     * @param initialAssignment
     *            {@code true} if this is the initial assignment, e.g. when an initial value of the retention property is set on the node,
     *            {@code false} otherwise
     */
    protected void processContentPropertyForRetentionUpdate(final Serializable propertyValue, final Date retentionTargetDate,
            final boolean initialAssignment)
    {
        if (propertyValue instanceof Collection<?>)
        {
            LOGGER.debug("Recursively processing collection of content property values: {}", propertyValue);

            ((Collection<?>) propertyValue).stream().filter(Serializable.class::isInstance).map(Serializable.class::cast)
                    .forEach(pv -> this.processContentPropertyForRetentionUpdate(pv, retentionTargetDate, initialAssignment));
        }
        else if (propertyValue instanceof ContentData)
        {
            final String contentUrl = ((ContentData) propertyValue).getContentUrl();
            if (this.isContentUrlSupported(contentUrl))
            {
                if (this.exists(contentUrl))
                {
                    final Path filePath = this.makeFilePath(contentUrl);
                    final File file = filePath.toFile();

                    if (this.retentionViaAccessTime)
                    {
                        LOGGER.debug("Handling retention update on {} via access time", contentUrl);
                        this.setRetentionViaAccessTime(file, retentionTargetDate, initialAssignment);
                    }
                }
                else
                {
                    LOGGER.debug("Store {} does not contain content URL {} - not handling retention update", this, contentUrl);
                }
            }
            else
            {
                LOGGER.debug("Store {} does not support content URL {} - not handling retention update", this, contentUrl);
            }
        }
        else
        {
            LOGGER.warn("Content property value {} is not supported for retention update", propertyValue);
        }
    }

    /**
     * Sets the retention target via the file access time.
     *
     * @param file
     *            the handle to the content file on which to set the retention target
     * @param retentionTargetDate
     *            the retention target date value
     * @param initialAssignment
     *            {@code true} if this is the initial assignment, e.g. immediately after writing the file or when an initial value of the
     *            retention property is set on the node, {@code false} otherwise
     */
    protected void setRetentionViaAccessTime(final File file, final Date retentionTargetDate, final boolean initialAssignment)
    {
        final FileTime accessTime = FileTime.from(retentionTargetDate.toInstant());

        if (!initialAssignment)
        {
            LOGGER.debug("Validating current access time of {} against target {}", file, accessTime);
            try
            {
                final BasicFileAttributes basicAttributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
                final FileTime currentAccessTime = basicAttributes.lastAccessTime();

                if (currentAccessTime.compareTo(accessTime) > 0)
                {
                    throw new ContentIOException("Illegal attempt to shorten retention by setting file access time to an earlier date");
                }
            }
            catch (final IOException ioex)
            {
                LOGGER.error("Failed to read current file attributes on {} to check access time against retention target {}", file,
                        accessTime, ioex);
                throw new ContentIOException("Failed read file access time", ioex);
            }
        }

        LOGGER.debug("Setting access time of {} to {}", file, accessTime);
        final BasicFileAttributeView basicAttributesView = Files.getFileAttributeView(file.toPath(), BasicFileAttributeView.class);
        try
        {
            basicAttributesView.setTimes(null, accessTime, null);
        }
        catch (final IOException ioex)
        {
            LOGGER.error("Failed to set retention to {} via access time on {}", accessTime, file, ioex);
            throw new ContentIOException("Failed to set retention via access time", ioex);
        }
    }
}
