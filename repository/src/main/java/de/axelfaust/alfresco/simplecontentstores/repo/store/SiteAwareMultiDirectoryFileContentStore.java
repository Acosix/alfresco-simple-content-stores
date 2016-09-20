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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.content.UnsupportedContentUrlException;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.util.Pair;
import org.alfresco.util.ParameterCheck;
import org.alfresco.util.PropertyCheck;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.axelfaust.alfresco.simplecontentstores.repo.store.context.ContentStoreContext;

/**
 * @author Axel Faust
 */
public class SiteAwareMultiDirectoryFileContentStore extends FileContentStore
{

    private static final Logger LOGGER = LoggerFactory.getLogger(SiteAwareMultiDirectoryFileContentStore.class);

    protected Map<String, String> rootAbsolutePathsBySitePreset;

    protected Map<String, String> rootAbsolutePathsBySite;

    protected Map<String, String> protocolsBySitePreset;

    protected Map<String, String> protocolsBySite;

    protected transient Map<String, File> rootDirectoriesByProtocol = new HashMap<>();

    protected boolean useSiteFolderInGenericDirectories;

    // TODO Implement moving
    protected boolean moveStoresOnNodeMove;

    // TODO Implement site / site preset specific contentLimitProvider

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        if (this.rootAbsolutePathsBySite != null)
        {
            PropertyCheck.mandatory(this, "protocolBySite", this.protocolsBySite);

            for (final Entry<String, String> entry : this.rootAbsolutePathsBySite.entrySet())
            {
                final String site = entry.getKey();
                final String protocol = this.protocolsBySite.get(site);
                PropertyCheck.mandatory(this, "protocolBySite." + site, protocol);

                if (this.rootDirectoriesByProtocol.containsKey(protocol))
                {
                    throw new ContentIOException("Failed to set up site aware content store - duplicate protocol: " + protocol, null);
                }

                final File directory = new File(entry.getValue());
                if (!directory.exists() && !directory.mkdirs())
                {
                    throw new ContentIOException("Failed to create store root: " + directory, null);
                }

                this.rootDirectoriesByProtocol.put(protocol, directory);
                entry.setValue(directory.getAbsolutePath());
            }
        }
        else
        {
            this.rootAbsolutePathsBySite = Collections.emptyMap();
        }

        if (this.rootAbsolutePathsBySitePreset != null)
        {
            PropertyCheck.mandatory(this, "protocolBySitePreset", this.protocolsBySitePreset);

            for (final Entry<String, String> entry : this.rootAbsolutePathsBySitePreset.entrySet())
            {
                final String sitePreset = entry.getKey();
                final String protocol = this.protocolsBySitePreset.get(sitePreset);
                PropertyCheck.mandatory(this, "protocolBySitePreset." + sitePreset, protocol);

                if (this.rootDirectoriesByProtocol.containsKey(protocol))
                {
                    throw new ContentIOException("Failed to set up site aware content store - duplicate protocol: " + protocol, null);
                }

                final File directory = new File(entry.getValue());
                if (!directory.exists() && !directory.mkdirs())
                {
                    throw new ContentIOException("Failed to create store root: " + directory, null);
                }

                this.rootDirectoriesByProtocol.put(protocol, directory);
                entry.setValue(directory.getAbsolutePath());
            }
        }
        else
        {
            this.rootAbsolutePathsBySitePreset = Collections.emptyMap();
        }

        if (this.rootDirectoriesByProtocol.containsKey(this.protocol))
        {
            throw new ContentIOException("Failed to set up site aware content store - duplicate protocol: " + this.protocol, null);
        }

        super.afterPropertiesSet();

        this.rootDirectoriesByProtocol.put(this.protocol, this.rootDirectory);
    }

    /**
     * @param rootAbsolutePathsBySitePreset
     *            the rootAbsolutePathsBySitePreset to set
     */
    public void setRootAbsolutePathsBySitePreset(final Map<String, String> rootAbsolutePathsBySitePreset)
    {
        this.rootAbsolutePathsBySitePreset = rootAbsolutePathsBySitePreset;
    }

    /**
     * @param rootAbsolutePathsBySite
     *            the rootAbsolutePathsBySite to set
     */
    public void setRootAbsolutePathsBySite(final Map<String, String> rootAbsolutePathsBySite)
    {
        this.rootAbsolutePathsBySite = rootAbsolutePathsBySite;
    }

    /**
     * @param protocolsBySitePreset
     *            the protocolsBySitePreset to set
     */
    public void setProtocolsBySitePreset(final Map<String, String> protocolsBySitePreset)
    {
        this.protocolsBySitePreset = protocolsBySitePreset;
    }

    /**
     * @param protocolsBySite
     *            the protocolsBySite to set
     */
    public void setProtocolsBySite(final Map<String, String> protocolsBySite)
    {
        this.protocolsBySite = protocolsBySite;
    }

    /**
     * @param useSiteFolderInGenericDirectories
     *            the useSiteFolderInGenericDirectories to set
     */
    public void setUseSiteFolderInGenericDirectories(final boolean useSiteFolderInGenericDirectories)
    {
        this.useSiteFolderInGenericDirectories = useSiteFolderInGenericDirectories;
    }

    /**
     * @param moveStoresOnNodeMove
     *            the moveStoresOnNodeMove to set
     */
    public void setMoveStoresOnNodeMove(final boolean moveStoresOnNodeMove)
    {
        this.moveStoresOnNodeMove = moveStoresOnNodeMove;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentReader getReader(final String contentUrl)
    {
        ParameterCheck.mandatoryString("contentUrl", contentUrl);

        final String effectiveContentUrl = this.determineEffectiveContentUrl(contentUrl, false);
        final ContentReader reader = super.getReader(effectiveContentUrl);
        return reader;
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("deprecation")
    @Override
    public void getUrls(final Date createdAfter, final Date createdBefore, final ContentUrlHandler handler)
    {
        for (final File rootDirectory : this.rootDirectoriesByProtocol.values())
        {
            this.getUrls(rootDirectory, handler, createdAfter, createdBefore);
        }

        LOGGER.debug("Listed all content URLS: \n   store: {}", this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ContentWriter getWriterInternal(final ContentReader existingContentReader, final String newContentUrl)
    {
        String effectiveNewContentUrl = null;
        if (newContentUrl != null)
        {
            effectiveNewContentUrl = this.determineEffectiveContentUrl(newContentUrl, false);
        }

        final ContentWriter contentWriter = super.getWriterInternal(existingContentReader, effectiveNewContentUrl);
        return contentWriter;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected String createNewFileStoreUrl()
    {
        final String baseNewContentUrl = super.createNewFileStoreUrl();
        final String effectiveNewContentUrl = this.determineEffectiveContentUrl(baseNewContentUrl, true);
        return effectiveNewContentUrl;
    }

    /**
     *
     * {@inheritDoc}
     */
    // only needed for deprecated getUrls
    @Deprecated
    @Override
    protected String makeContentUrl(final File file)
    {
        final String path = file.getAbsolutePath();

        final List<Entry<String, String>> absoluteRootPaths = new ArrayList<>();
        absoluteRootPaths.addAll(this.rootAbsolutePathsBySitePreset.entrySet());
        absoluteRootPaths.addAll(this.rootAbsolutePathsBySite.entrySet());

        // check if it belongs to this store
        String protocol = null;
        int absolutePathLength = -1;
        if (path.startsWith(this.rootAbsolutePath))
        {
            absolutePathLength = this.rootAbsolutePath.length();
            protocol = this.protocol;
        }
        else
        {
            for (final Entry<String, String> rootAbsolutePathEntry : this.rootAbsolutePathsBySitePreset.entrySet())
            {
                if (path.startsWith(rootAbsolutePathEntry.getValue()))
                {
                    absolutePathLength = rootAbsolutePathEntry.getValue().length();
                    protocol = this.protocolsBySitePreset.get(rootAbsolutePathEntry.getKey());
                    break;
                }
            }

            if (protocol == null)
            {
                for (final Entry<String, String> rootAbsolutePathEntry : this.rootAbsolutePathsBySite.entrySet())
                {
                    if (path.startsWith(rootAbsolutePathEntry.getValue()))
                    {
                        absolutePathLength = rootAbsolutePathEntry.getValue().length();
                        protocol = this.protocolsBySite.get(rootAbsolutePathEntry.getKey());
                        break;
                    }
                }
            }
        }

        if (protocol == null)
        {
            throw new AlfrescoRuntimeException(
                    "File does not fall below the store's root: \n" + "   file: " + file + "\n" + "   store: " + this);
        }

        // strip off the file separator char, if present
        int index = absolutePathLength;
        if (path.charAt(index) == File.separatorChar)
        {
            index++;
        }

        // strip off the root path and adds the protocol prefix
        String url = protocol + ContentStore.PROTOCOL_DELIMITER + path.substring(index);
        // replace '\' with '/' so that URLs are consistent across all filesystems
        url = url.replace('\\', '/');
        // done
        return url;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected File makeFile(final String protocol, final String relativePath)
    {
        // Check the protocol
        if (!StoreConstants.WILDCARD_PROTOCOL.equals(protocol) && !this.rootDirectoriesByProtocol.containsKey(protocol))
        {
            throw new UnsupportedContentUrlException(this, protocol + PROTOCOL_DELIMITER + relativePath);
        }

        // get the file
        File rootDirectory = this.rootDirectoriesByProtocol.get(protocol);
        if (rootDirectory == null)
        {
            rootDirectory = this.rootDirectory;
        }
        final File file = new File(rootDirectory, relativePath);

        this.ensureFileInContentStore(file);

        // done
        return file;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void ensureFileInContentStore(final File file)
    {
        boolean contained = false;

        final String fileNormalizedAbsoultePath = FilenameUtils.normalize(file.getAbsolutePath());
        final List<String> absoluteRootPaths = new ArrayList<>();
        absoluteRootPaths.add(this.rootAbsolutePath);
        absoluteRootPaths.addAll(this.rootAbsolutePathsBySitePreset.values());
        absoluteRootPaths.addAll(this.rootAbsolutePathsBySite.values());

        for (final String rootAbsolutePath : absoluteRootPaths)
        {
            final String rootNormalizedAbsolutePath = FilenameUtils.normalize(rootAbsolutePath);
            contained = fileNormalizedAbsoultePath.startsWith(rootNormalizedAbsolutePath);

            if (contained)
            {
                break;
            }
        }

        if (!contained)
        {
            throw new ContentIOException("Access to files outside of content store root is not allowed: " + file);
        }
    }

    protected String determineEffectiveContentUrl(final String baseContentUrl, final boolean allowProtocolOverride)
    {
        String effectiveContentUrl = baseContentUrl;

        final Object site = ContentStoreContext.getContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_SITE);
        final Object sitePreset = ContentStoreContext.getContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_SITE_PRESET);

        final Pair<String, String> urlParts = this.getContentUrlParts(baseContentUrl);
        final String protocol = urlParts.getFirst();
        // need to correct protocol + contentUrl
        if ((StoreConstants.WILDCARD_PROTOCOL.equals(protocol) || allowProtocolOverride) && (site != null || sitePreset != null))
        {
            LOGGER.debug("Determining effective content URL for base URL {}, and context attributes site {} and site preset {}",
                    baseContentUrl, site, sitePreset);

            String effectiveProtocol = null;
            boolean genericDirectory = false;

            if (site != null)
            {
                effectiveProtocol = this.protocolsBySite.get(site);
            }

            if (effectiveProtocol == null && sitePreset != null)
            {
                effectiveProtocol = this.protocolsBySitePreset.get(sitePreset);
                genericDirectory = true;
            }

            if (effectiveProtocol == null)
            {
                effectiveProtocol = this.protocol;
                genericDirectory = true;
            }

            final StringBuilder stringBuilder = new StringBuilder(baseContentUrl.length() * 2);
            stringBuilder.append(effectiveProtocol);
            stringBuilder.append(PROTOCOL_DELIMITER);
            if (site != null && genericDirectory && this.useSiteFolderInGenericDirectories)
            {
                stringBuilder.append(site);
                stringBuilder.append("/");
            }
            stringBuilder.append(urlParts.getSecond());
            effectiveContentUrl = stringBuilder.toString();

            LOGGER.debug("Determined effective content URL {} for base URL {}, and context attributes site {} and site preset {}",
                    effectiveContentUrl, baseContentUrl, site, sitePreset);
        }
        return effectiveContentUrl;
    }
}
