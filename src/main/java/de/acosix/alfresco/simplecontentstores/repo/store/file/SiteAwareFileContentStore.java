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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.util.EqualsHelper;
import org.alfresco.util.Pair;
import org.alfresco.util.ParameterCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.acosix.alfresco.simplecontentstores.repo.store.ContentUrlUtils;
import de.acosix.alfresco.simplecontentstores.repo.store.StoreConstants;
import de.acosix.alfresco.simplecontentstores.repo.store.context.ContentStoreContext;

/**
 * @author Axel Faust
 */
public class SiteAwareFileContentStore extends FileContentStore
{

    private static final String SITE_PREFIX_INDICATOR = "_site_";

    private static final Logger LOGGER = LoggerFactory.getLogger(SiteAwareFileContentStore.class);

    protected boolean useSiteFolderInGenericDirectories;

    /**
     * @return the useSiteFolderInGenericDirectories
     */
    public boolean isUseSiteFolderInGenericDirectories()
    {
        return this.useSiteFolderInGenericDirectories;
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
     * {@inheritDoc}
     */
    @Override
    public boolean exists(final String contentUrl)
    {
        ParameterCheck.mandatoryString("contentUrl", contentUrl);

        final Pair<String, String> urlParts = this.getContentUrlParts(contentUrl);
        final String protocol = urlParts.getFirst();
        final String effectiveContentUrl = this.checkAndAdjustInboundContentUrl(contentUrl,
                EqualsHelper.nullSafeEquals(StoreConstants.WILDCARD_PROTOCOL, protocol));

        final boolean result = super.exists(effectiveContentUrl);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentReader getReader(final String contentUrl)
    {
        ParameterCheck.mandatoryString("contentUrl", contentUrl);

        final Pair<String, String> urlParts = this.getContentUrlParts(contentUrl);
        final String protocol = urlParts.getFirst();
        final String effectiveContentUrl = this.checkAndAdjustInboundContentUrl(contentUrl,
                EqualsHelper.nullSafeEquals(StoreConstants.WILDCARD_PROTOCOL, protocol));

        final ContentReader reader = super.getReader(effectiveContentUrl);
        return reader;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public boolean delete(final String contentUrl)
    {
        ParameterCheck.mandatoryString("contentUrl", contentUrl);

        final String effectiveContentUrl = this.checkAndAdjustInboundContentUrl(contentUrl, false);

        final boolean result = super.delete(effectiveContentUrl);
        return result;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected ContentWriter getWriterInternal(final ContentReader existingContentReader, final String newContentUrl)
    {
        String effectiveContentUrl = null;
        if (newContentUrl != null)
        {
            effectiveContentUrl = this.checkAndAdjustInboundContentUrl(newContentUrl, true);
        }

        final ContentWriter writer = super.getWriterInternal(existingContentReader, effectiveContentUrl);
        return writer;
    }

    protected String checkAndAdjustInboundContentUrl(final String contentUrl, final boolean allowExistingPrefixChange)
    {
        LOGGER.debug("Checking and potentially adjusting inbound content URL {}", contentUrl);
        String effectiveContentUrl = contentUrl;

        final List<String> prefixes = ContentUrlUtils.extractPrefixes(effectiveContentUrl);
        final int sitePrefixIndex = prefixes.indexOf(SITE_PREFIX_INDICATOR);

        final Object site = ContentStoreContext.getContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_SITE);
        if (this.useSiteFolderInGenericDirectories && site != null)
        {
            if (sitePrefixIndex == -1)
            {
                LOGGER.debug("Adding site {} prefix to inbound content URL {}", site, effectiveContentUrl);
                effectiveContentUrl = ContentUrlUtils.getContentUrlWithPrefixes(effectiveContentUrl, SITE_PREFIX_INDICATOR,
                        String.valueOf(site));
            }
            else if (allowExistingPrefixChange && !EqualsHelper.nullSafeEquals(prefixes.get(sitePrefixIndex + 1), site)
                    && prefixes.size() > sitePrefixIndex + 1)
            {
                LOGGER.debug("Upadting site {} prefix to {} on inbound content URL {}", prefixes.get(sitePrefixIndex + 1), site,
                        effectiveContentUrl);
                final List<String> alteredPrefixes = new ArrayList<>(prefixes);
                alteredPrefixes.set(sitePrefixIndex + 1, String.valueOf(site));
                effectiveContentUrl = ContentUrlUtils.getContentUrlWithPrefixes(ContentUrlUtils.getBaseContentUrl(effectiveContentUrl),
                        alteredPrefixes.toArray(new String[0]));
            }
        }
        else if (allowExistingPrefixChange && sitePrefixIndex != -1 && prefixes.size() > sitePrefixIndex + 1)
        {
            LOGGER.debug("Removing site {} prefix from inbound content URL {}", prefixes.get(sitePrefixIndex + 1), effectiveContentUrl);
            final List<String> alteredPrefixes = new ArrayList<>(prefixes);
            alteredPrefixes.remove(sitePrefixIndex + 1);
            alteredPrefixes.remove(sitePrefixIndex);
            effectiveContentUrl = ContentUrlUtils.getContentUrlWithPrefixes(ContentUrlUtils.getBaseContentUrl(effectiveContentUrl),
                    alteredPrefixes.toArray(new String[0]));
        }
        else
        {
            LOGGER.debug("No adaption of content URL is required");
        }
        return effectiveContentUrl;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected String createNewFileStoreUrl()
    {
        String effectiveNewContentUrl = super.createNewFileStoreUrl();

        final Object site = ContentStoreContext.getContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_SITE);
        if (this.useSiteFolderInGenericDirectories && site != null)
        {
            LOGGER.debug("Adding site {} prefix to new content URL {}", site, effectiveNewContentUrl);
            effectiveNewContentUrl = ContentUrlUtils.getContentUrlWithPrefixes(effectiveNewContentUrl, SITE_PREFIX_INDICATOR,
                    String.valueOf(site));
        }

        return effectiveNewContentUrl;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected Path makeFilePath(final String contentUrl)
    {
        final String baseContentUrl = ContentUrlUtils.getBaseContentUrl(contentUrl);

        final Pair<String, String> urlParts = this.getContentUrlParts(baseContentUrl);
        final String protocol = urlParts.getFirst();

        String relativePath = urlParts.getSecond();
        if (this.useSiteFolderInGenericDirectories)
        {
            final List<String> prefixes = ContentUrlUtils.extractPrefixes(contentUrl);
            final int indexSiteIndicator = prefixes.indexOf(SITE_PREFIX_INDICATOR);
            if (indexSiteIndicator != -1 && prefixes.size() > indexSiteIndicator + 1)
            {
                final String site = prefixes.get(indexSiteIndicator + 1);
                LOGGER.debug("Prepending site {} to relative content path {}", site, relativePath);
                relativePath = site + "/" + relativePath;
            }
        }

        return this.makeFilePath(protocol, relativePath);
    }
}
