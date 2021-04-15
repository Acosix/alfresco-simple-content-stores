/*
 * Copyright 2017 - 2021 Acosix GmbH
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
import org.alfresco.repo.content.NodeContentContext;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;

/**
 * This initializer will enrich the currently active {@link ContentStoreContext content store context} with default information about the
 * node and content property referenced.
 *
 * @author Axel Faust
 */
public class DefaultAttributesInitializer implements ContentStoreContextInitializer
{

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void initialize(final ContentContext context)
    {
        if (context instanceof NodeContentContext)
        {
            final NodeRef nodeRef = ((NodeContentContext) context).getNodeRef();
            final QName propertyQName = ((NodeContentContext) context).getPropertyQName();

            ContentStoreContext.setContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_NODE, nodeRef);
            ContentStoreContext.setContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_PROPERTY, propertyQName);
        }

    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void initialize(final NodeRef node, final QName propertyQName)
    {
        ContentStoreContext.setContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_NODE, node);
        ContentStoreContext.setContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_PROPERTY, propertyQName);
    }

}
