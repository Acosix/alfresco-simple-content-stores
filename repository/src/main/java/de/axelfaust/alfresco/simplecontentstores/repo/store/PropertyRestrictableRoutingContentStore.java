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

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.alfresco.repo.content.ContentContext;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.content.NodeContentContext;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.dictionary.PropertyDefinition;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.ParameterCheck;
import org.alfresco.util.PropertyCheck;

/**
 * @author Axel Faust
 */
public abstract class PropertyRestrictableRoutingContentStore<CD> extends MoveCapableCommonRoutingContentStore<CD>
{

    protected NamespaceService namespaceService;

    protected List<String> routeContentPropertyNames;

    protected transient Set<QName> routeContentPropertyQNames;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "namespaceService", this.namespaceService);

        super.afterPropertiesSet();

        this.afterPropertiesSet_setupRouteContentProperties();
    }

    /**
     * @param namespaceService
     *            the namespaceService to set
     */
    public void setNamespaceService(final NamespaceService namespaceService)
    {
        this.namespaceService = namespaceService;
    }

    /**
     * @param routeContentPropertyNames
     *            the routeContentPropertyNames to set
     */
    public void setRouteContentPropertyNames(final List<String> routeContentPropertyNames)
    {
        this.routeContentPropertyNames = routeContentPropertyNames;
    }

    /**
     * @param fallbackStore
     *            the fallbackStore to set
     */
    public void setFallbackStore(final ContentStore fallbackStore)
    {
        this.fallbackStore = fallbackStore;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected boolean isRoutable(final ContentContext ctx)
    {
        final QName contentPropertyQName = ctx instanceof NodeContentContext ? ((NodeContentContext) ctx).getPropertyQName() : null;
        final boolean result = this.routeContentPropertyQNames == null || this.routeContentPropertyQNames.contains(contentPropertyQName);

        return result;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void processContentPropertiesMove(final NodeRef nodeRef, final Map<QName, Serializable> contentProperties,
            final CD customData)
    {
        final Map<QName, Serializable> updates = new HashMap<>();
        for (final Entry<QName, Serializable> contentPropertyEntry : contentProperties.entrySet())
        {
            final QName contentProperty = contentPropertyEntry.getKey();
            if (this.routeContentPropertyQNames == null || this.routeContentPropertyQNames.contains(contentProperty))
            {
                this.processContentPropertyMove(nodeRef, contentProperty, contentPropertyEntry.getValue(), updates, customData);
            }
        }

        if (!updates.isEmpty())
        {
            this.nodeService.addProperties(nodeRef, updates);
        }
    }

    private void afterPropertiesSet_setupRouteContentProperties()
    {
        if (this.routeContentPropertyNames != null && !this.routeContentPropertyNames.isEmpty())
        {
            this.routeContentPropertyQNames = new HashSet<>();
            for (final String routePropertyName : this.routeContentPropertyNames)
            {
                final QName routePropertyQName = QName.resolveToQName(this.namespaceService, routePropertyName);
                ParameterCheck.mandatory("routePropertyQName", routePropertyQName);

                final PropertyDefinition contentPropertyDefinition = this.dictionaryService.getProperty(routePropertyQName);
                if (contentPropertyDefinition == null
                        || !DataTypeDefinition.CONTENT.equals(contentPropertyDefinition.getDataType().getName()))
                {
                    throw new IllegalStateException(routePropertyName + " is not a valid content model property of type d:content");
                }
                this.routeContentPropertyQNames.add(routePropertyQName);
            }
        }
    }
}
