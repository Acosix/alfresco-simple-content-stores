/*
 * Copyright 2017 - 2020 Acosix GmbH
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
package de.acosix.alfresco.simplecontentstores.repo.store.file;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.content.AbstractContentStore;
import org.alfresco.repo.content.ContentLimitProvider;
import org.alfresco.repo.content.ContentLimitProvider.SimpleFixedLimitProvider;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.content.ContentStoreCreatedEvent;
import org.alfresco.repo.content.EmptyContentReader;
import org.alfresco.repo.content.UnsupportedContentUrlException;
import org.alfresco.repo.content.filestore.FileContentUrlProvider;
import org.alfresco.repo.content.filestore.SpoofedTextContentReader;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.util.Pair;
import org.alfresco.util.PropertyCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.extensions.surf.util.ParameterCheck;

import de.acosix.alfresco.simplecontentstores.repo.store.ContentUrlUtils;
import de.acosix.alfresco.simplecontentstores.repo.store.StoreConstants;

/**
 * This implementation of a file-based content store is heavily borrowed from {@link org.alfresco.repo.content.filestore.FileContentStore}
 * with adaptions designed to work around limitations of that implementation. This class (and its 90% copied code) wouldn't be necessary if
 * Alfresco didn't excessively use restricting member visibilities.
 *
 * @author Axel Faust
 */
public class FileContentStore extends AbstractContentStore
        implements ApplicationContextAware, ApplicationListener<ContextRefreshedEvent>, InitializingBean
{

    protected static final String STORE_PROTOCOL = org.alfresco.repo.content.filestore.FileContentStore.STORE_PROTOCOL;

    protected static final String SPOOF_PROTOCOL = org.alfresco.repo.content.filestore.FileContentStore.SPOOF_PROTOCOL;

    private static final Logger LOGGER = LoggerFactory.getLogger(FileContentStore.class);

    protected ApplicationContext applicationContext;

    protected Map<String, Serializable> extendedEventParameters;

    protected transient File rootDirectory;

    protected String rootAbsolutePath;

    protected String protocol = STORE_PROTOCOL;

    protected boolean allowRandomAccess;

    protected boolean readOnly;

    protected boolean deleteEmptyDirs = true;

    protected FileContentUrlProvider fileContentUrlProvider;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "rootAbsolutePath", this.rootAbsolutePath);
        PropertyCheck.mandatory(this, "protocol", this.protocol);

        if (this.extendedEventParameters == null)
        {
            this.extendedEventParameters = Collections.<String, Serializable> emptyMap();
        }

        this.rootDirectory = new File(this.rootAbsolutePath);
        if (!this.rootDirectory.exists() && !this.rootDirectory.mkdirs())
        {
            throw new ContentIOException("Failed to create store root: " + this.rootDirectory, null);
        }

        if (this.fileContentUrlProvider == null)
        {
            this.fileContentUrlProvider = new TimeBasedFileContentUrlProvider();
            ((TimeBasedFileContentUrlProvider) this.fileContentUrlProvider).setStoreProtocol(this.protocol);
        }
        else
        {
            final String createNewFileStoreUrl = this.fileContentUrlProvider.createNewFileStoreUrl();
            if (!createNewFileStoreUrl.startsWith(this.protocol + ContentStore.PROTOCOL_DELIMITER))
            {
                this.fileContentUrlProvider = new AlternativeProtocolFileContentUrlProviderFacade(this.fileContentUrlProvider,
                        this.protocol);
            }
        }

        this.rootDirectory = this.rootDirectory.getAbsoluteFile();
        this.rootAbsolutePath = this.rootDirectory.getAbsolutePath();

        if (this.applicationContext != null)
        {
            this.applicationContext.publishEvent(new ContentStoreCreatedEvent(this, this.extendedEventParameters));
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException
    {
        this.applicationContext = applicationContext;
    }

    /**
     * @param extendedEventParameters
     *            the extendedEventParameters to set
     */
    public void setExtendedEventParameters(final Map<String, Serializable> extendedEventParameters)
    {
        // decouple map
        this.extendedEventParameters = extendedEventParameters != null ? new HashMap<>(extendedEventParameters) : null;
    }

    /**
     * @param rootAbsolutePath
     *            the rootAbsolutePath to set
     */
    public void setRootAbsolutePath(final String rootAbsolutePath)
    {
        this.rootAbsolutePath = rootAbsolutePath;
    }

    /**
     * Simple alias to {@link #setRootAbsolutePath(String)} for compatibility with previously used {@code FileContentStoreFactoryBean}
     *
     * @param rootDirectory
     *            the rootDirectory to set
     */
    public void setRootDirectory(final String rootDirectory)
    {
        this.rootAbsolutePath = rootDirectory;
    }

    /**
     * @param protocol
     *            the protocol to set
     */
    public void setProtocol(final String protocol)
    {
        this.protocol = protocol;
    }

    /**
     * @param allowRandomAccess
     *            the allowRandomAccess to set
     */
    public void setAllowRandomAccess(final boolean allowRandomAccess)
    {
        this.allowRandomAccess = allowRandomAccess;
    }

    /**
     * @param readOnly
     *            the readOnly to set
     */
    public void setReadOnly(final boolean readOnly)
    {
        this.readOnly = readOnly;
    }

    /**
     * @param deleteEmptyDirs
     *            the deleteEmptyDirs to set
     */
    public void setDeleteEmptyDirs(final boolean deleteEmptyDirs)
    {
        this.deleteEmptyDirs = deleteEmptyDirs;
    }

    /**
     *
     * @param limit
     *            the fixed content limit to set
     */
    public void setFixedLimit(final long limit)
    {
        if (limit < 0 && limit != ContentLimitProvider.NO_LIMIT)
        {
            throw new IllegalArgumentException("fixedLimit must be non-negative");
        }
        this.setContentLimitProvider(new SimpleFixedLimitProvider(limit));
    }

    /**
     * @param fileContentUrlProvider
     *            the fileContentUrlProvider to set
     */
    public void setFileContentUrlProvider(final FileContentUrlProvider fileContentUrlProvider)
    {
        this.fileContentUrlProvider = fileContentUrlProvider;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void onApplicationEvent(final ContextRefreshedEvent event)
    {
        if (this.extendedEventParameters == null)
        {
            this.extendedEventParameters = Collections.<String, Serializable> emptyMap();
        }

        if (event.getSource() == this.applicationContext)
        {
            final ApplicationContext context = event.getApplicationContext();
            context.publishEvent(new ContentStoreCreatedEvent(this, this.extendedEventParameters));
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public long getSpaceFree()
    {
        return this.rootDirectory.getFreeSpace();
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public long getSpaceTotal()
    {
        return this.rootDirectory.getTotalSpace();
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public String getRootLocation()
    {
        String rootLocation;
        try
        {
            rootLocation = this.rootDirectory.getCanonicalPath();
        }
        catch (final IOException | SecurityException e)
        {
            LOGGER.warn("Unabled to return root location", e);
            rootLocation = super.getRootLocation();
        }

        return rootLocation;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public boolean isWriteSupported()
    {
        return !this.readOnly;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isContentUrlSupported(final String contentUrl)
    {
        ParameterCheck.mandatoryString("contentUrl", contentUrl);

        // improved check to avoid the implicit, more expensive getReader call performed in super implementation
        // also trims down on logging
        final String effectiveContentUrl = ContentUrlUtils.checkAndReplaceWildcardProtocol(contentUrl, this.protocol);
        final Pair<String, String> urlParts = this.getContentUrlParts(effectiveContentUrl);
        final String protocol = urlParts.getFirst();

        boolean contentUrlSupported;
        if (SPOOF_PROTOCOL.equals(protocol) || this.protocol.equals(protocol))
        {
            LOGGER.debug("Content URL {} with effective protocol {} is supported by store {} with protocol {}", contentUrl, protocol, this,
                    this.protocol);
            contentUrlSupported = true;
        }
        else
        {
            LOGGER.debug("Content URL {} with effective protocol {} is not supported by store {} with protocol {}", contentUrl, protocol,
                    this, this.protocol);
            contentUrlSupported = false;
        }
        return contentUrlSupported;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean exists(final String contentUrl)
    {
        ParameterCheck.mandatoryString("contentUrl", contentUrl);

        final String effectiveContentUrl = ContentUrlUtils.checkAndReplaceWildcardProtocol(contentUrl, this.protocol);
        final Pair<String, String> urlParts = this.getContentUrlParts(effectiveContentUrl);
        final String protocol = urlParts.getFirst();

        boolean result;
        if (protocol.equals(SPOOF_PROTOCOL))
        {
            result = true;
        }
        else
        {
            final Path filePath = this.makeFilePath(effectiveContentUrl);
            result = Files.exists(filePath) && !Files.isDirectory(filePath);
            LOGGER.debug("Content URL {} {} as a file", contentUrl, result ? "exists" : "does not exist");
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentReader getReader(final String contentUrl)
    {
        ParameterCheck.mandatoryString("contentUrl", contentUrl);

        final String effectiveContentUrl = ContentUrlUtils.checkAndReplaceWildcardProtocol(contentUrl, this.protocol);
        final Pair<String, String> urlParts = this.getContentUrlParts(effectiveContentUrl);
        final String protocol = urlParts.getFirst();

        ContentReader reader;
        if (protocol.equals(SPOOF_PROTOCOL))
        {
            reader = new SpoofedTextContentReader(effectiveContentUrl);
        }
        else
        {
            try
            {
                LOGGER.debug("Checking if {} exists as a file to construct a reader", contentUrl);
                final Path filePath = this.makeFilePath(effectiveContentUrl);
                if (Files.exists(filePath) && !Files.isDirectory(filePath))
                {
                    final FileContentReaderImpl fileContentReader = new FileContentReaderImpl(filePath.toFile(), effectiveContentUrl);

                    fileContentReader.setAllowRandomAccess(this.allowRandomAccess);

                    reader = fileContentReader;
                }
                else
                {
                    reader = new EmptyContentReader(effectiveContentUrl);
                }

                LOGGER.debug("Created content reader: \n   url: {}\n   file: {}\n   reader: {}", effectiveContentUrl, filePath, reader);
            }
            catch (final UnsupportedContentUrlException e)
            {
                throw e;
            }
        }
        return reader;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean delete(final String contentUrl)
    {
        ParameterCheck.mandatoryString("contentUrl", contentUrl);

        if (this.readOnly)
        {
            throw new UnsupportedOperationException("This store is currently read-only: " + this);
        }

        boolean deleted;
        final String effectiveContentUrl = ContentUrlUtils.checkAndReplaceWildcardProtocol(contentUrl, this.protocol);
        final Pair<String, String> urlParts = this.getContentUrlParts(effectiveContentUrl);
        final String protocol = urlParts.getFirst();

        if (protocol.equals(SPOOF_PROTOCOL))
        {
            // This is not a failure but the content can never actually be deleted
            deleted = false;
        }
        else
        {
            LOGGER.debug("Checking if {} exists as a file to be deleted", contentUrl);
            final Path filePath = this.makeFilePath(effectiveContentUrl);
            if (!Files.isRegularFile(filePath))
            {
                LOGGER.debug("Path {} does not denote an existing content file - treating as already deleted", filePath);
                deleted = true;
            }
            else
            {
                if (filePath.toFile().canWrite())
                {
                    try
                    {
                        Files.delete(filePath);
                        deleted = true;
                        LOGGER.debug("Deleted content file {}", filePath);
                    }
                    catch (final IOException e)
                    {
                        LOGGER.warn("Error deleting content file {}", filePath, e);
                        deleted = false;
                    }
                }
                else
                {
                    LOGGER.debug("Path {} is not writable - not attempting deletion", filePath);
                    deleted = false;
                }
            }

            if (this.deleteEmptyDirs && deleted)
            {
                this.deleteEmptyParents(filePath, this.rootDirectory);
            }
        }

        return deleted;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder(36);
        sb.append(this.getClass().getSimpleName()).append("[ root=").append(this.rootDirectory).append(", allowRandomAccess=")
                .append(this.allowRandomAccess).append(", readOnly=").append(this.readOnly).append("]");
        return sb.toString();
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
            final FileContentWriterImpl writer = new FileContentWriterImpl(file, contentUrl, existingContentReader);

            if (this.contentLimitProvider != null)
            {
                writer.setContentLimitProvider(this.contentLimitProvider);
            }

            writer.setAllowRandomAccess(this.allowRandomAccess);

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
     * Creates a file for the specifically provided content URL. The URL may not already be in use.
     * <p>
     * The store prefix is stripped off the URL and the rest of the URL used directly to create a file.
     *
     * @param newContentUrl
     *            the specific URL to use, which may not be in use
     * @return a new and unique file
     * @throws IOException
     *             if the file or parent directories couldn't be created or if the URL is already in use.
     * @throws UnsupportedOperationException
     *             if the store is read-only
     *
     * @see #setReadOnly(boolean)
     */
    protected File createNewFile(final String newContentUrl) throws IOException
    {
        if (this.readOnly)
        {
            throw new UnsupportedOperationException("This store is currently read-only: " + this);
        }

        LOGGER.debug("Creating new file for {}", newContentUrl);
        final Path filePath = this.makeFilePath(newContentUrl);

        if (Files.exists(filePath))
        {
            throw new ContentIOException("When specifying a URL for new content, the URL may not be in use already. \n" + "   store: "
                    + this + "\n" + "   new URL: " + newContentUrl);
        }

        final Path parentPath = filePath.getParent();
        // unlikely but possible due to API definition
        if (parentPath != null)
        {
            Files.createDirectories(parentPath);
        }
        Files.createFile(filePath);
        LOGGER.debug("Created content file {}", filePath);

        return filePath.toFile();
    }

    /**
     * Takes the file absolute path, strips off the root path of the store and appends the store URL prefix.
     *
     * @param file
     *            the file from which to create the URL
     * @return the equivalent content URL
     */
    // only needed for deprecated getUrls
    @Deprecated
    protected String makeContentUrl(final File file)
    {
        final String path = file.getAbsolutePath();
        if (!path.startsWith(this.rootAbsolutePath))
        {
            throw new AlfrescoRuntimeException(
                    "File does not fall below the store's root: \n" + "   file: " + file + "\n" + "   store: " + this);
        }
        int index = this.rootAbsolutePath.length();
        if (path.charAt(index) == File.separatorChar)
        {
            index++;
        }

        String url = this.protocol + ContentStore.PROTOCOL_DELIMITER + path.substring(index);
        url = url.replace('\\', '/');
        return url;
    }

    /**
     * Creates a file path from the given relative URL.
     *
     * @param contentUrl
     *            the content URL including the protocol prefix
     * @return a file representing the URL - the file may or may not exist
     * @throws UnsupportedContentUrlException
     *             if the URL is invalid and doesn't support the {@link FileContentStore#STORE_PROTOCOL correct protocol}
     */
    protected Path makeFilePath(final String contentUrl)
    {
        final String baseContentUrl = ContentUrlUtils.getBaseContentUrl(contentUrl);
        final Pair<String, String> urlParts = this.getContentUrlParts(baseContentUrl);
        final String protocol = urlParts.getFirst();
        final String relativePath = urlParts.getSecond();
        return this.makeFilePath(protocol, relativePath);
    }

    /**
     * Creates a file path based on the content URL parts.
     *
     * @param protocol
     *            must be {@link ContentStore#PROTOCOL_DELIMITER} for this class
     * @param relativePath
     *            the relative path to turn into a file
     * @return the file path
     */
    protected Path makeFilePath(final String protocol, final String relativePath)
    {
        if (!StoreConstants.WILDCARD_PROTOCOL.equals(protocol) && !this.protocol.equals(protocol))
        {
            throw new UnsupportedContentUrlException(this, protocol + PROTOCOL_DELIMITER + relativePath);
        }

        final Path rootPath = this.rootDirectory.toPath();
        final Path filePath = rootPath.resolve(relativePath);
        if (!filePath.startsWith(rootPath))
        {
            throw new ContentIOException("Access to files outside of content store root is not allowed: " + filePath);
        }

        return filePath;
    }

    /**
     * Creates a new content URL.
     *
     * @return the new and unique content URL
     */
    protected String createNewFileStoreUrl()
    {
        final String newContentUrl = this.fileContentUrlProvider.createNewFileStoreUrl();
        return newContentUrl;
    }

    protected void deleteEmptyParents(final Path filePath, final File rootDirectory)
    {
        final Path rootDirectoryPath = rootDirectory.toPath();

        Path curPath = filePath.getParent();
        try
        {
            while (curPath != null && Files.exists(curPath) && !Files.isSameFile(rootDirectoryPath, curPath))
            {
                if (Files.isSymbolicLink(curPath))
                {
                    LOGGER.debug("Aborting deletion of empty parents as {} is a symbolic link", curPath);
                    break;
                }

                if (!Files.isDirectory(curPath))
                {
                    LOGGER.debug("Aborting deletion of empty parents as {} is not a directory", curPath);
                    break;
                }

                final long children;
                try (Stream<Path> stream = Files.list(curPath))
                {
                    children = stream.count();
                }

                if (children != 0)
                {
                    LOGGER.debug("Aborting deletion of empty parents as {} is not empty", curPath);
                    break;
                }

                LOGGER.trace("Deleting empty parent {}", curPath);
                Files.delete(curPath);
                LOGGER.debug("Deleted empty parent {}", curPath);
                curPath = curPath.getParent();
            }
        }
        catch (final IOException e)
        {
            LOGGER.warn("Error deleting empty parent directories", e);
        }
    }
}
