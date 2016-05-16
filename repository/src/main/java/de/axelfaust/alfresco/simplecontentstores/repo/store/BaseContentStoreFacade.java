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

import java.util.Date;

import org.alfresco.repo.content.ContentContext;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.content.ContentStoreCaps;
import org.alfresco.repo.tenant.TenantDeployer;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.util.ParameterCheck;

/**
 * @author Axel Faust
 */
public class BaseContentStoreFacade<T extends ContentStore> implements ContentStore, ContentStoreCaps
{

    protected final T delegate;

    public BaseContentStoreFacade(final T delegate)
    {
        ParameterCheck.mandatory("delegate", delegate);
        this.delegate = delegate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isContentUrlSupported(final String contentUrl)
    {
        return this.delegate.isContentUrlSupported(contentUrl);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isWriteSupported()
    {
        return this.delegate.isWriteSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getSpaceFree()
    {
        return this.delegate.getSpaceFree();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getSpaceTotal()
    {
        return this.delegate.getSpaceTotal();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getRootLocation()
    {
        return this.delegate.getRootLocation();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean exists(final String contentUrl)
    {
        return this.delegate.exists(contentUrl);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentReader getReader(final String contentUrl)
    {
        return this.delegate.getReader(contentUrl);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentWriter getWriter(final ContentContext context)
    {
        return this.delegate.getWriter(context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("deprecated")
    public void getUrls(final ContentUrlHandler handler) throws ContentIOException
    {
        this.delegate.getUrls(handler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("deprecated")
    public void getUrls(final Date createdAfter, final Date createdBefore, final ContentUrlHandler handler) throws ContentIOException
    {
        this.delegate.getUrls(createdAfter, createdBefore, handler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean delete(final String contentUrl)
    {
        return this.delegate.delete(contentUrl);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TenantDeployer getTenantRoutingContentStore()
    {
        final TenantDeployer deployer = this.delegate instanceof ContentStoreCaps ? ((ContentStoreCaps) this.delegate)
                .getTenantRoutingContentStore() : null;
        return deployer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TenantDeployer getTenantDeployer()
    {
        final TenantDeployer deployer = this.delegate instanceof ContentStoreCaps ? ((ContentStoreCaps) this.delegate).getTenantDeployer()
                : null;
        return deployer;
    }

}
