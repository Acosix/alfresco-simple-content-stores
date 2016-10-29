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
package de.axelfaust.alfresco.simplecontentstores.repo.store.file;

import java.io.File;
import java.util.List;

import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.util.Pair;
import org.alfresco.util.ParameterCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.axelfaust.alfresco.simplecontentstores.repo.store.ContentUrlUtils;
import de.axelfaust.alfresco.simplecontentstores.repo.store.context.ContentStoreContext;

/**
 * @author Axel Faust
 */
public class SiteAwareFileContentStore extends FileContentStore
{

    private static final String SITE_PREFIX_INDICATOR = "_site_";

    private static final Logger LOGGER = LoggerFactory.getLogger(SiteAwareFileContentStore.class);

    protected boolean useSiteFolderInGenericDirectories;

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

        final String effectiveContentUrl = this.checkAndAdjustInboundContentUrl(contentUrl);

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

        final String effectiveContentUrl = this.checkAndAdjustInboundContentUrl(contentUrl);

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

        final String effectiveContentUrl = this.checkAndAdjustInboundContentUrl(contentUrl);

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
            effectiveContentUrl = this.checkAndAdjustInboundContentUrl(newContentUrl);
        }

        final ContentWriter writer = super.getWriterInternal(existingContentReader, effectiveContentUrl);
        return writer;
    }

    protected String checkAndAdjustInboundContentUrl(final String contentUrl)
    {
        String effectiveContentUrl = contentUrl;

        final Object site = ContentStoreContext.getContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_SITE);
        if (this.useSiteFolderInGenericDirectories && site != null)
        {
            final List<String> prefixes = ContentUrlUtils.extractPrefixes(effectiveContentUrl);
            final int sitePrefixIndex = prefixes.indexOf(SITE_PREFIX_INDICATOR);
            if (sitePrefixIndex == -1)
            {
                LOGGER.debug("Adding site {} prefix to inbound content URL {}", site, effectiveContentUrl);
                effectiveContentUrl = ContentUrlUtils.getContentUrlWithPrefixes(effectiveContentUrl, SITE_PREFIX_INDICATOR,
                        String.valueOf(site));
            }
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
    protected File makeFile(final String contentUrl)
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
                LOGGER.debug("Prepending site {} to relative content path", site, relativePath);
                relativePath = site + "/" + relativePath;
            }
        }

        return this.makeFile(protocol, relativePath);
    }
}
