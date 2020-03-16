package de.acosix.alfresco.simplecontentstores.repo.store.file;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.alfresco.repo.transaction.TransactionalResourceHelper;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.util.PropertyCheck;
import org.alfresco.util.transaction.TransactionListener;
import org.alfresco.util.transaction.TransactionSupportUtil;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.acosix.alfresco.simplecontentstores.repo.store.ContentUrlUtils;

/**
 * Instances of this specialised file content store handle advanced requirements of document archival in regular file system-based
 * locations. This includes generation of file hash checksums during file writing as well as setting the file's read-only flag as one
 * of the last operations in the overall transaction to ensure the archival hardware properly locks the content file. Generated checksums
 * are available for {@link #getContentUrlHashes() retrieval} by any behaviours that wish to persist this information on the affected node.
 *
 * @author Axel Faust
 */
public class ArchiveFileContentStore extends FileContentStore implements TransactionListener
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

        PropertyCheck.mandatory(this, "digestAlgorithm", this.digestAlgorithm);
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
        final Optional<File> nonReadOnlyFile = files.stream().filter(f -> !f.setReadOnly()).findFirst();
        nonReadOnlyFile.ifPresent(f -> {
            throw new ContentIOException("Failed to set readOnly flag on " + f);
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

            writer.addListener(() -> {
                final Map<String, String> map = TransactionalResourceHelper.getMap(TXN_CONTENT_URL_HASHES);
                final byte[] digest = writer.getDigest();
                final char[] digestCh = Hex.encodeHex(digest, false);
                final String digestStr = new String(digestCh);
                final String digestVal = this.digestAlgorithm.toLowerCase(Locale.ENGLISH) + ':' + digestStr;
                map.put(writer.getContentUrl(), digestVal);

                final List<File> files = TransactionalResourceHelper.getList(TXN_WRITTEN_FILES);
                if (files.isEmpty())
                {
                    TransactionSupportUtil.bindListener(this, COMMIT_ORDER_DAO);
                }
                files.add(file);
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
}
