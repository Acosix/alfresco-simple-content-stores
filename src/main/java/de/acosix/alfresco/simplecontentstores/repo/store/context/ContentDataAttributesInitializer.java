/*
 * Copyright 2017 - 2022 Acosix GmbH
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

import java.io.Serializable;

import org.alfresco.repo.content.ContentContext;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.content.NodeContentContext;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.PropertyCheck;
import org.springframework.beans.factory.InitializingBean;

/**
 * This initializer will enrich the currently active {@link ContentStoreContext content store context} with site-related information from
 * the current {@link ContentStore#getWriter(ContentContext) getWriter}/{@link ContentService#getReader(NodeRef, QName) getReader}-call if
 * possible. The initializer will check if the current call context is provides information about the affected node and qualified content
 * property name, and initialise the {@link ContentStoreContext#DEFAULT_ATTRIBUTE_CONTENT_DATA content data} attribute with the current
 * value of that property.
 *
 * @author Axel Faust
 */
public class ContentDataAttributesInitializer implements ContentStoreContextInitializer, InitializingBean
{

    protected NodeService nodeService;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "nodeService", this.nodeService);
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
     * {@inheritDoc}
     */
    @Override
    public void initialize(final ContentContext context)
    {
        final Object contentDataAttribute = ContentStoreContext.getContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_CONTENT_DATA);
        if (contentDataAttribute == null && context instanceof NodeContentContext)
        {
            final NodeRef nodeRef = ((NodeContentContext) context).getNodeRef();
            final QName propertyQName = ((NodeContentContext) context).getPropertyQName();

            final Serializable currentValue = AuthenticationUtil.runAsSystem(() -> this.nodeService.getProperty(nodeRef, propertyQName));

            if (currentValue instanceof ContentData)
            {
                ContentStoreContext.setContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_CONTENT_DATA, currentValue);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize(final NodeRef node, final QName propertyQName)
    {
        final Object contentDataAttribute = ContentStoreContext.getContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_CONTENT_DATA);
        if (contentDataAttribute == null)
        {
            final Serializable currentValue = AuthenticationUtil.runAsSystem(() -> this.nodeService.getProperty(node, propertyQName));

            if (currentValue instanceof ContentData)
            {
                ContentStoreContext.setContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_CONTENT_DATA, currentValue);
            }
        }
    }
}
