/*
 * Copyright 2017 - 2019 Acosix GmbH
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

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.alfresco.repo.content.ContentLimitProvider;
import org.alfresco.repo.content.ContentLimitProvider.NoLimitProvider;
import org.alfresco.repo.content.ContentLimitProvider.SimpleFixedLimitProvider;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.content.filestore.FileContentUrlProvider;
import org.alfresco.repo.tenant.AbstractTenantRoutingContentStore;
import org.alfresco.repo.tenant.TenantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

/**
 * This is an alternative to the default out-of-the-box {@link org.alfresco.repo.tenant.TenantRoutingFileContentStore}
 * to be used as a drop-in replacement which uses this modules custom {@link FileContentStore} instead of the
 * {@link org.alfresco.repo.content.filestore.FileContentStore default implementation} in order to support the extended configuration
 * options of that implementation.
 *
 * @author Axel Faust
 */
public class TenantRoutingFileContentStore extends AbstractTenantRoutingContentStore
{

    private static final Logger LOGGER = LoggerFactory.getLogger(TenantRoutingFileContentStore.class);

    protected static final String STORE_PROTOCOL = org.alfresco.repo.content.filestore.FileContentStore.STORE_PROTOCOL;

    protected ContentLimitProvider contentLimitProvider = new NoLimitProvider();

    protected FileContentUrlProvider fileContentUrlProvider;

    // Alfresco TenantRoutingFileContentStore does not support the following configuration properties

    protected String protocol = STORE_PROTOCOL;

    protected boolean allowRandomAccess;

    protected boolean readOnly;

    protected boolean deleteEmptyDirs = true;

    /**
     * @param contentLimitProvider
     *            the contentLimitProvider to set
     */
    public void setContentLimitProvider(final ContentLimitProvider contentLimitProvider)
    {
        this.contentLimitProvider = contentLimitProvider;
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
     * Simple alias to {@link #setRootLocation(String)} to support identical parameter names for this implementation and base
     * {@link FileContentStore}
     *
     * @param rootAbsolutePath
     *            the rootAbsolutePath to set
     */
    public void setRootAbsolutePath(final String rootAbsolutePath)
    {
        this.setRootLocation(rootAbsolutePath);
    }

    /**
     * Simple alias to {@link #setRootLocation(String)} to support identical parameter names for this implementation and base
     * {@link FileContentStore}
     *
     * @param rootDirectory
     *            the rootDirectory to set
     */
    public void setRootDirectory(final String rootDirectory)
    {
        this.setRootLocation(rootDirectory);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected ContentStore initContentStore(final ApplicationContext ctx, final String contentRoot)
    {
        final String domain = this.tenantService.getCurrentUserDomain();
        final Map<String, Serializable> extendedEventParams = new HashMap<>();
        if (!TenantService.DEFAULT_DOMAIN.equals(domain))
        {
            LOGGER.debug("Initialising new tenant file content store for {} with root directory {}", domain, contentRoot);
            extendedEventParams.put("Tenant", domain);
        }
        else
        {
            LOGGER.debug("Initialising new tenant file content store for default tenant with root directory {}", contentRoot);
        }

        final FileContentStore fileContentStore = new FileContentStore();
        fileContentStore.setApplicationContext(ctx);
        fileContentStore.setRootDirectory(contentRoot);
        fileContentStore.setExtendedEventParameters(extendedEventParams);

        fileContentStore.setProtocol(this.protocol);
        fileContentStore.setAllowRandomAccess(this.allowRandomAccess);
        fileContentStore.setReadOnly(this.readOnly);
        fileContentStore.setDeleteEmptyDirs(this.deleteEmptyDirs);

        if (this.contentLimitProvider != null)
        {
            fileContentStore.setContentLimitProvider(this.contentLimitProvider);
        }

        if (this.fileContentUrlProvider != null)
        {
            fileContentStore.setFileContentUrlProvider(this.fileContentUrlProvider);
        }
        fileContentStore.afterPropertiesSet();

        return fileContentStore;
    }
}
