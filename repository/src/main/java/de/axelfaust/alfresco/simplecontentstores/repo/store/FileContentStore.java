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

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Map;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.content.AbstractContentStore;
import org.alfresco.repo.content.ContentLimitProvider;
import org.alfresco.repo.content.ContentLimitProvider.SimpleFixedLimitProvider;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.content.ContentStoreCreatedEvent;
import org.alfresco.repo.content.EmptyContentReader;
import org.alfresco.repo.content.UnsupportedContentUrlException;
import org.alfresco.repo.content.filestore.FileContentReader;
import org.alfresco.repo.content.filestore.FileContentWriter;
import org.alfresco.repo.content.filestore.SpoofedTextContentReader;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.util.Deleter;
import org.alfresco.util.GUID;
import org.alfresco.util.Pair;
import org.alfresco.util.ParameterCheck;
import org.alfresco.util.PropertyCheck;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

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

    private static final Method READER_SET_ALLOW_RANDOM_ACCESS;

    private static final Method WRITER_SET_ALLOW_RANDOM_ACCESS;

    // this is immensively ugly but Alfresco using friggin' package-protected forces us to do this lest we copy entire class
    static
    {
        try
        {
            READER_SET_ALLOW_RANDOM_ACCESS = FileContentReader.class.getDeclaredMethod("setAllowRandomAccess", boolean.class);
            READER_SET_ALLOW_RANDOM_ACCESS.setAccessible(true);

            WRITER_SET_ALLOW_RANDOM_ACCESS = FileContentWriter.class.getDeclaredMethod("setAllowRandomAccess", boolean.class);
            WRITER_SET_ALLOW_RANDOM_ACCESS.setAccessible(true);
        }
        catch (final NoSuchMethodException | SecurityException e)
        {
            throw new AlfrescoRuntimeException(
                    "Incompatible Alfresco version - FileContentReader/FileContentWriter do not provide a setAllowRandomgAccess method", e);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(FileContentStore.class);

    protected ApplicationContext applicationContext;

    protected Map<String, Serializable> extendedEventParameters;

    protected transient File rootDirectory;

    protected String rootAbsolutePath;

    protected String protocol = STORE_PROTOCOL;

    protected boolean allowRandomAccess;

    protected boolean readOnly;

    protected boolean deleteEmptyDirs = true;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "applicationContext", this.applicationContext);

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

        this.rootDirectory = this.rootDirectory.getAbsoluteFile();
        this.rootAbsolutePath = this.rootDirectory.getAbsolutePath();

        this.applicationContext.publishEvent(new ContentStoreCreatedEvent(this, this.extendedEventParameters));
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
        this.extendedEventParameters = extendedEventParameters;
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
     * Simple alias to {@ink #setRootAbsolutePath(String)} for compatibility with previously used {@code FileContentStoreFactoryBean}
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

        // Once the context has been refreshed, we tell other interested beans about the existence of this content store
        // (e.g. for monitoring purposes)
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
        catch (final Throwable e)
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
            final File file = this.makeFile(effectiveContentUrl);
            result = file.exists();
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
        // Handle the spoofed URL
        if (protocol.equals(SPOOF_PROTOCOL))
        {
            reader = new SpoofedTextContentReader(effectiveContentUrl);
        }
        else
        {
            // else, it's a real file we are after
            try
            {
                final File file = this.makeFile(effectiveContentUrl);
                if (file.exists())
                {
                    final FileContentReader fileContentReader = new FileContentReader(file, effectiveContentUrl);

                    READER_SET_ALLOW_RANDOM_ACCESS.invoke(fileContentReader, Boolean.valueOf(this.allowRandomAccess));

                    reader = fileContentReader;
                }
                else
                {
                    reader = new EmptyContentReader(effectiveContentUrl);
                }

                // done
                LOGGER.debug("Created content reader: \n   url: {}\n   file: {}\n   reader: {]", effectiveContentUrl, file, reader);
            }
            catch (final UnsupportedContentUrlException e)
            {
                // This can go out directly
                throw e;
            }
            catch (final Throwable e)
            {
                throw new ContentIOException("Failed to get reader for URL: " + effectiveContentUrl, e);
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
            // Handle regular files based on the real files
            final File file = this.makeFile(effectiveContentUrl);
            if (!file.exists())
            {
                // File does not exist
                deleted = true;
            }
            else
            {
                deleted = file.delete();
            }

            // Delete empty parents regardless of whether the file was ignore above.
            if (this.deleteEmptyDirs && deleted)
            {
                Deleter.deleteEmptyParents(file, this.getRootLocation());
            }

            // done
            LOGGER.debug("Delete content directly: \n   store: {}\n   url: {}", this, effectiveContentUrl);
        }

        return deleted;
    }

    /**
     * {@inheritDoc}
     */
    @Deprecated
    @Override
    public void getUrls(final Date createdAfter, final Date createdBefore, final ContentUrlHandler handler)
    {
        // recursively get all files within the root
        this.getUrls(this.rootDirectory, handler, createdAfter, createdBefore);
        // done
        LOGGER.debug("Listed all content URLS: \n   store: {}", this);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder(36);
        sb.append("FileContentStore").append("[ root=").append(this.rootDirectory).append(", allowRandomAccess=")
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
            File file = null;
            if (newContentUrl == null) // a specific URL was not supplied
            {
                contentUrl = this.createNewFileStoreUrl();
            }
            else
            // the URL has been given
            {
                contentUrl = ContentUrlUtils.checkAndReplaceWildcardProtocol(newContentUrl, this.protocol);
            }
            file = this.createNewFile(contentUrl);
            // create the writer
            final FileContentWriter writer = new FileContentWriter(file, contentUrl, existingContentReader);

            if (this.contentLimitProvider != null)
            {
                writer.setContentLimitProvider(this.contentLimitProvider);
            }

            WRITER_SET_ALLOW_RANDOM_ACCESS.invoke(writer, Boolean.valueOf(this.allowRandomAccess));

            // done
            LOGGER.debug("Created content writer: \n   writer: {}", writer);
            return writer;
        }
        catch (final Throwable e)
        {
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

        final File file = this.makeFile(newContentUrl);

        // create the directory, if it doesn't exist
        final File dir = file.getParentFile();
        if (!dir.exists())
        {
            this.makeDirectory(dir);
        }

        // create a new, empty file
        final boolean created = file.createNewFile();
        if (!created)
        {
            throw new ContentIOException("When specifying a URL for new content, the URL may not be in use already. \n" + "   store: "
                    + this + "\n" + "   new URL: " + newContentUrl);
        }

        // done
        return file;
    }

    /**
     * Synchronized and retrying directory creation. Repeated attempts will be made to create the directory, subject to a limit on the
     * number of retries.
     *
     * @param dir
     *            the directory to create
     * @throws IOException
     *             if an IO error occurs
     */
    protected synchronized void makeDirectory(final File dir) throws IOException
    {
        final int tries = 0;
        boolean created = false;

        try
        {
            // 20 attempts with 20 ms wait each time
            while (!dir.exists() && !created && tries < 20)
            {
                created = dir.mkdirs();

                if (!created)
                {
                    // Wait
                    this.wait(20L);
                }
            }
        }
        catch (final InterruptedException e)
        {
            // can't just ignore an interruption
            throw new ContentIOException("Interrupted during directory creation", e);
        }

        if (!created && !dir.exists())
        {
            // It still didn't succeed
            throw new ContentIOException("Failed to create directory for file storage: " + dir);
        }
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
        // check if it belongs to this store
        if (!path.startsWith(this.rootAbsolutePath))
        {
            throw new AlfrescoRuntimeException(
                    "File does not fall below the store's root: \n" + "   file: " + file + "\n" + "   store: " + this);
        }
        // strip off the file separator char, if present
        int index = this.rootAbsolutePath.length();
        if (path.charAt(index) == File.separatorChar)
        {
            index++;
        }

        // strip off the root path and adds the protocol prefix
        String url = this.protocol + ContentStore.PROTOCOL_DELIMITER + path.substring(index);
        // replace '\' with '/' so that URLs are consistent across all filesystems
        url = url.replace('\\', '/');
        // done
        return url;
    }

    /**
     * Creates a file from the given relative URL.
     *
     * @param contentUrl
     *            the content URL including the protocol prefix
     * @return a file representing the URL - the file may or may not exist
     * @throws UnsupportedContentUrlException
     *             if the URL is invalid and doesn't support the {@link FileContentStore#STORE_PROTOCOL correct protocol}
     */
    protected File makeFile(final String contentUrl)
    {
        final String baseContentUrl = ContentUrlUtils.getBaseContentUrl(contentUrl);
        final Pair<String, String> urlParts = this.getContentUrlParts(baseContentUrl);
        final String protocol = urlParts.getFirst();
        final String relativePath = urlParts.getSecond();
        return this.makeFile(protocol, relativePath);
    }

    /**
     * Creates a file object based on the content URL parts.
     *
     * @param protocol
     *            must be {@link ContentStore#PROTOCOL_DELIMITER} for this class
     * @param relativePath
     *            the relative path to turn into a file
     * @return the file for the path
     */
    protected File makeFile(final String protocol, final String relativePath)
    {
        // Check the protocol
        if (!StoreConstants.WILDCARD_PROTOCOL.equals(protocol) && !this.protocol.equals(protocol))
        {
            throw new UnsupportedContentUrlException(this, protocol + PROTOCOL_DELIMITER + relativePath);
        }
        // get the file
        final File file = new File(this.rootDirectory, relativePath);

        this.ensureFileInContentStore(file);

        // done
        return file;
    }

    /**
     * Checks that the file to be accessed by this content store is actually content inside of the root location.
     *
     * @param file
     *            the file to check for containment in this store
     */
    protected void ensureFileInContentStore(final File file)
    {
        final String fileNormalizedAbsoultePath = FilenameUtils.normalize(file.getAbsolutePath());
        final String rootNormalizedAbsolutePath = FilenameUtils.normalize(this.rootAbsolutePath);

        if (!fileNormalizedAbsoultePath.startsWith(rootNormalizedAbsolutePath))
        {
            throw new ContentIOException("Access to files outside of content store root is not allowed: " + file);
        }
    }

    /**
     * Creates a new content URL.
     *
     * @return the new and unique content URL
     */
    protected String createNewFileStoreUrl()
    {
        final Calendar calendar = new GregorianCalendar();
        final int year = calendar.get(Calendar.YEAR);
        final int month = calendar.get(Calendar.MONTH) + 1; // 0-based
        final int day = calendar.get(Calendar.DAY_OF_MONTH);
        final int hour = calendar.get(Calendar.HOUR_OF_DAY);
        final int minute = calendar.get(Calendar.MINUTE);
        // create the URL
        final StringBuilder sb = new StringBuilder(20);
        sb.append(this.protocol).append(ContentStore.PROTOCOL_DELIMITER).append(year).append('/').append(month).append('/').append(day)
                .append('/').append(hour).append('/').append(minute).append('/').append(GUID.generate()).append(".bin");
        final String newContentUrl = sb.toString();
        // done
        return newContentUrl;
    }

    /**
     * Returns a list of all files within the given directory and all subdirectories.
     *
     * @param directory
     *            the current directory to get the files from
     * @param handler
     *            the callback to use for each URL
     * @param createdAfter
     *            only get URLs for content create after this date
     * @param createdBefore
     *            only get URLs for content created before this date
     */
    @Deprecated
    protected void getUrls(final File directory, final ContentUrlHandler handler, final Date createdAfter, final Date createdBefore)
    {
        final File[] files = directory.listFiles();
        if (files == null)
        {
            // the directory has disappeared
            throw new ContentIOException("Failed list files in folder: " + directory);
        }
        for (final File file : files)
        {
            if (file.isDirectory())
            {
                // we have a subdirectory - recurse
                this.getUrls(file, handler, createdAfter, createdBefore);
            }
            else
            {
                // check the created date of the file
                final long lastModified = file.lastModified();
                if (createdAfter != null && lastModified < createdAfter.getTime())
                {
                    // file is too old
                    continue;
                }
                else if (createdBefore != null && lastModified > createdBefore.getTime())
                {
                    // file is too young
                    continue;
                }
                // found a file - create the URL
                final String contentUrl = this.makeContentUrl(file);
                // Callback
                handler.handle(contentUrl);
            }
        }
    }
}
