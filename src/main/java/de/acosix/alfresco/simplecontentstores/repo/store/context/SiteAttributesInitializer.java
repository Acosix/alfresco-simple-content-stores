/*
 * Copyright 2017 - 2024 Acosix GmbH
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
package de.acosix.alfresco.simplecontentstores.repo.store.context;

import org.alfresco.repo.content.ContentContext;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.content.NodeContentContext;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.site.SiteInfo;
import org.alfresco.service.cmr.site.SiteService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.PropertyCheck;
import org.springframework.beans.factory.InitializingBean;

/**
 * This initializer will enrich the currently active {@link ContentStoreContext content store context} with site-related information from
 * the current {@link ContentStore#getWriter(ContentContext) getWriter}/{@link ContentService#getReader(NodeRef, QName) getReader}-call if
 * possible. The initializer will check if the {@link NodeRef node} in the current call context is contained in a site, the interceptor will
 * transfer the {@link ContentStoreContext#DEFAULT_ATTRIBUTE_SITE site} and {@link ContentStoreContext#DEFAULT_ATTRIBUTE_SITE_PRESET site
 * preset} identifiers as attributes in the content store context.
 *
 * @author Axel Faust
 */
public class SiteAttributesInitializer implements ContentStoreContextInitializer, InitializingBean
{

    protected SiteService siteService;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "siteService", this.siteService);
    }

    /**
     * @param siteService
     *            the siteService to set
     */
    public void setSiteService(final SiteService siteService)
    {
        this.siteService = siteService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize(final ContentContext context)
    {
        final Object siteAttribute = ContentStoreContext.getContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_SITE);
        if (siteAttribute == null && context instanceof NodeContentContext)
        {
            final NodeRef nodeRef = ((NodeContentContext) context).getNodeRef();
            final SiteInfo site = AuthenticationUtil.runAsSystem(() -> this.siteService.getSite(nodeRef));

            if (site != null)
            {
                ContentStoreContext.setContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_SITE, site.getShortName());
                ContentStoreContext.setContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_SITE_PRESET, site.getSitePreset());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize(final NodeRef node, final QName propertyQName)
    {
        final Object siteAttribute = ContentStoreContext.getContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_SITE);
        if (siteAttribute == null)
        {
            final SiteInfo site = AuthenticationUtil.runAsSystem(() -> this.siteService.getSite(node));

            if (site != null)
            {
                ContentStoreContext.setContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_SITE, site.getShortName());
                ContentStoreContext.setContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_SITE_PRESET, site.getSitePreset());
            }
        }
    }
}
