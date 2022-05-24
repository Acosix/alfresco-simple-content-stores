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
package de.acosix.alfresco.simplecontentstores.repo.store.routing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.ContentContext;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.content.NodeContentContext;
import org.alfresco.repo.node.NodeServicePolicies.OnSetNodeTypePolicy;
import org.alfresco.repo.policy.Behaviour.NotificationFrequency;
import org.alfresco.repo.policy.JavaBehaviour;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.dictionary.PropertyDefinition;
import org.alfresco.service.cmr.dictionary.TypeDefinition;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.PropertyCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Instances of this class provide the ability to route content based on the type of the node.
 *
 * @author Axel Faust
 */
public class TypeRoutingContentStore extends PropertyRestrictableRoutingContentStore<Void> implements OnSetNodeTypePolicy
{

    private static final Logger LOGGER = LoggerFactory.getLogger(TypeRoutingContentStore.class);

    protected Map<String, ContentStore> storeByTypeName;

    protected Map<QName, ContentStore> storeByTypeQName;

    protected boolean moveStoresOnChange;

    protected String moveStoresOnChangeOptionPropertyName;

    protected transient QName moveStoresOnChangeOptionPropertyQName;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        super.afterPropertiesSet();

        this.afterPropertiesSet_validateSelectors();
        this.afterPropertiesSet_setupStoreData();
        this.afterPropertiesSet_setupChangePolicies();
    }

    /**
     * @param storeByTypeName
     *            the storeByTypeName to set
     */
    public void setStoreByTypeName(final Map<String, ContentStore> storeByTypeName)
    {
        this.storeByTypeName = storeByTypeName;
    }

    /**
     * @param moveStoresOnChange
     *            the moveStoresOnChange to set
     */
    public void setMoveStoresOnChange(final boolean moveStoresOnChange)
    {
        this.moveStoresOnChange = moveStoresOnChange;
    }

    /**
     * @param moveStoresOnChangeOptionPropertyName
     *            the moveStoresOnChangeOptionPropertyName to set
     */
    public void setMoveStoresOnChangeOptionPropertyName(final String moveStoresOnChangeOptionPropertyName)
    {
        this.moveStoresOnChangeOptionPropertyName = moveStoresOnChangeOptionPropertyName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSetNodeType(final NodeRef nodeRef, final QName oldType, final QName newType)
    {
        if (StoreRef.STORE_REF_WORKSPACE_SPACESSTORE.equals(nodeRef.getStoreRef()))
        {
            LOGGER.debug("Processing node type change for {} from {} to {}", nodeRef, oldType, newType);

            final ContentStore oldStore = this.resolveStoreForType(nodeRef, oldType);
            final ContentStore newStore = this.resolveStoreForType(nodeRef, newType);

            if (oldStore != newStore)
            {
                LOGGER.debug("Node {} was changed to type for which content sthould be stored in a different store", nodeRef);
                this.checkAndProcessContentPropertiesMove(nodeRef);
            }
            else
            {
                LOGGER.debug("Node {} was not changed to type for which content sthould be stored in a different store", nodeRef);
            }
        }
    }

    protected void checkAndProcessContentPropertiesMove(final NodeRef affectedNode)
    {
        this.checkAndProcessContentPropertiesMove(affectedNode, this.moveStoresOnChange, this.moveStoresOnChangeOptionPropertyQName, null);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected ContentStore selectStoreForContentDataMove(final NodeRef nodeRef, final QName propertyQName, final ContentData contentData,
            final Void customData)
    {
        final QName nodeTypeQName = this.nodeService.getType(nodeRef);
        ContentStore targetStore = this.resolveStoreForType(nodeRef, nodeTypeQName);
        if (targetStore == null)
        {
            LOGGER.debug("Store-specific logic could not select a store to move {} - delegating to super.selectStoreForContentDataMove",
                    contentData);
            targetStore = super.selectStoreForContentDataMove(nodeRef, propertyQName, contentData, customData);
        }

        return targetStore;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected ContentStore selectWriteStoreFromRoutes(final ContentContext ctx)
    {
        final ContentStore writeStore;
        if (ctx instanceof NodeContentContext)
        {
            final NodeRef nodeRef = ((NodeContentContext) ctx).getNodeRef();
            final QName nodeTypeQName = this.nodeService.getType(nodeRef);

            ContentStore targetStore = this.resolveStoreForType(nodeRef, nodeTypeQName);

            if (targetStore == null)
            {
                LOGGER.debug("No store registered for type {} or any ancestor - delegating to super.selectWiteStoreFromRoute",
                        nodeTypeQName);
                targetStore = super.selectWriteStoreFromRoutes(ctx);
            }
            writeStore = targetStore;
        }
        else
        {
            LOGGER.debug("ContentContext {} cannot be handled - delegating to super.selectWiteStoreFromRoute", ctx);
            writeStore = super.selectWriteStoreFromRoutes(ctx);
        }

        return writeStore;
    }

    /**
     * Resolves the content store to use for a specific node type.
     *
     * @param nodeRef
     *            the node for which a store is to be resolved
     * @param nodeTypeQName
     *            the type of the node being resolved
     * @return the store for the type or {@code null} if no store could was registered for the type or any of its ancestors
     */
    protected ContentStore resolveStoreForType(final NodeRef nodeRef, final QName nodeTypeQName)
    {
        LOGGER.debug("Looking up store for node {} and type {}", nodeRef, nodeTypeQName);
        ContentStore targetStore = null;
        QName currentTypeQName = nodeTypeQName;
        while (targetStore == null && currentTypeQName != null)
        {
            if (this.storeByTypeQName.containsKey(currentTypeQName))
            {
                LOGGER.debug("Using store defined for type {} (same or ancestor of node type {})", currentTypeQName, nodeTypeQName);
                targetStore = this.storeByTypeQName.get(currentTypeQName);
            }

            currentTypeQName = this.dictionaryService.getType(currentTypeQName).getParentName();
        }
        return targetStore;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean isRoutable(final ContentContext ctx)
    {
        final boolean result;

        final String contentUrl = ctx.getContentUrl();
        if (ctx instanceof NodeContentContext)
        {
            final NodeRef nodeRef = ((NodeContentContext) ctx).getNodeRef();
            result = nodeRef != null && super.isRoutable(ctx);
        }
        else
        {
            result = contentUrl != null;
        }
        return result;
    }

    private void afterPropertiesSet_validateSelectors()
    {
        PropertyCheck.mandatory(this, "storeByTypeName", this.storeByTypeName);
        if (this.storeByTypeName.isEmpty())
        {
            throw new IllegalStateException("No stores have been defined for node types");
        }

        this.storeByTypeQName = new HashMap<>();
        this.storeByTypeName.forEach((typeName, store) -> {
            final QName typeQName = QName.resolveToQName(this.namespaceService, typeName);
            if (typeQName == null)
            {
                throw new IllegalStateException(typeQName + " cannot be resolved to a qualified name");
            }
            final TypeDefinition type = this.dictionaryService.getType(typeQName);
            if (type == null)
            {
                throw new IllegalStateException(typeQName + " cannot be resolved to a registered node type");
            }

            this.storeByTypeQName.put(typeQName, store);
        });
    }

    private void afterPropertiesSet_setupStoreData()
    {
        PropertyCheck.mandatory(this, "storeByTypeName", this.storeByTypeName);
        if (this.storeByTypeName.isEmpty())
        {
            throw new IllegalStateException("No stores have been defined for node types");
        }

        this.allStores = new ArrayList<>();
        for (final ContentStore store : this.storeByTypeName.values())
        {
            if (!this.allStores.contains(store))
            {
                this.allStores.add(store);
            }
        }

        if (!this.allStores.contains(this.fallbackStore))
        {
            this.allStores.add(this.fallbackStore);
        }
    }

    private void afterPropertiesSet_setupChangePolicies()
    {
        if (this.moveStoresOnChangeOptionPropertyName != null)
        {
            this.moveStoresOnChangeOptionPropertyQName = QName.resolveToQName(this.namespaceService,
                    this.moveStoresOnChangeOptionPropertyName);
            PropertyCheck.mandatory(this, "moveStoresOnChangeOptionPropertyQName", this.moveStoresOnChangeOptionPropertyQName);

            final PropertyDefinition moveStoresOnChangeOptionPropertyDefinition = this.dictionaryService
                    .getProperty(this.moveStoresOnChangeOptionPropertyQName);
            if (moveStoresOnChangeOptionPropertyDefinition == null
                    || !DataTypeDefinition.BOOLEAN.equals(moveStoresOnChangeOptionPropertyDefinition.getDataType().getName())
                    || moveStoresOnChangeOptionPropertyDefinition.isMultiValued())
            {
                throw new IllegalStateException(this.moveStoresOnChangeOptionPropertyName
                        + " is not a valid content model property of type single-valued d:boolean");
            }
        }

        if (this.moveStoresOnChange || this.moveStoresOnChangeOptionPropertyQName != null)
        {
            this.policyComponent.bindClassBehaviour(OnSetNodeTypePolicy.QNAME, ContentModel.TYPE_BASE,
                    new JavaBehaviour(this, "onSetNodeType", NotificationFrequency.EVERY_EVENT));
        }
    }
}
