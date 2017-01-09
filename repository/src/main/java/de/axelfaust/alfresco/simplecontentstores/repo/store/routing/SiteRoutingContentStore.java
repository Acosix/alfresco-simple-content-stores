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
package de.axelfaust.alfresco.simplecontentstores.repo.store.routing;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.ContentContext;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.copy.CopyServicePolicies.OnCopyCompletePolicy;
import org.alfresco.repo.node.NodeServicePolicies.OnMoveNodePolicy;
import org.alfresco.repo.policy.Behaviour.NotificationFrequency;
import org.alfresco.repo.policy.JavaBehaviour;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.dictionary.PropertyDefinition;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.PropertyCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.axelfaust.alfresco.simplecontentstores.repo.store.context.ContentStoreContext;

/**
 * @author Axel Faust
 */
public class SiteRoutingContentStore extends PropertyRestrictableRoutingContentStore<Void> implements OnCopyCompletePolicy, OnMoveNodePolicy
{

    private static final Logger LOGGER = LoggerFactory.getLogger(SiteRoutingContentStore.class);

    protected Map<String, ContentStore> storeBySitePreset;

    protected Map<String, ContentStore> storeBySite;

    protected boolean moveStoresOnNodeMoveOrCopy;

    protected String moveStoresOnNodeMoveOrCopyName;

    protected transient QName moveStoresOnNodeMoveOrCopyQName;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        super.afterPropertiesSet();

        this.afterPropertiesSet_setupStoreData();
        this.afterPropertiesSet_setupChangePolicies();
    }

    /**
     * @param storeBySitePreset
     *            the storeBySitePreset to set
     */
    public void setStoreBySitePreset(final Map<String, ContentStore> storeBySitePreset)
    {
        this.storeBySitePreset = storeBySitePreset;
    }

    /**
     * @param storeBySite
     *            the storeBySite to set
     */
    public void setStoreBySite(final Map<String, ContentStore> storeBySite)
    {
        this.storeBySite = storeBySite;
    }

    /**
     * @param moveStoresOnNodeMoveOrCopy
     *            the moveStoresOnNodeMoveOrCopy to set
     */
    public void setMoveStoresOnNodeMoveOrCopy(final boolean moveStoresOnNodeMoveOrCopy)
    {
        this.moveStoresOnNodeMoveOrCopy = moveStoresOnNodeMoveOrCopy;
    }

    /**
     * @param moveStoresOnNodeMoveOrCopyName
     *            the moveStoresOnNodeMoveOrCopyName to set
     */
    public void setMoveStoresOnNodeMoveOrCopyName(final String moveStoresOnNodeMoveOrCopyName)
    {
        this.moveStoresOnNodeMoveOrCopyName = moveStoresOnNodeMoveOrCopyName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onMoveNode(final ChildAssociationRef oldChildAssocRef, final ChildAssociationRef newChildAssocRef)
    {
        // only act on active nodes which can actually be in a site
        final NodeRef movedNode = oldChildAssocRef.getChildRef();
        if (StoreRef.STORE_REF_WORKSPACE_SPACESSTORE.equals(movedNode.getStoreRef()))
        {
            this.checkAndProcessContentPropertiesMove(movedNode);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCopyComplete(final QName classRef, final NodeRef sourceNodeRef, final NodeRef targetNodeRef, final boolean copyToNewNode,
            final Map<NodeRef, NodeRef> copyMap)
    {
        // only act on active nodes which can actually be in a site
        if (StoreRef.STORE_REF_WORKSPACE_SPACESSTORE.equals(targetNodeRef.getStoreRef()))
        {
            this.checkAndProcessContentPropertiesMove(targetNodeRef);
        }
    }

    protected void checkAndProcessContentPropertiesMove(final NodeRef targetNodeRef)
    {
        final Map<QName, Serializable> properties = this.nodeService.getProperties(targetNodeRef);

        boolean doMove = false;
        if (this.moveStoresOnNodeMoveOrCopyQName != null)
        {
            final Serializable moveStoresOnChangeOptionValue = properties.get(this.moveStoresOnNodeMoveOrCopyQName);
            // explicit value wins
            if (moveStoresOnChangeOptionValue != null)
            {
                doMove = Boolean.TRUE.equals(moveStoresOnChangeOptionValue);
            }
            else
            {
                doMove = this.moveStoresOnNodeMoveOrCopy;
            }
        }
        else
        {
            doMove = this.moveStoresOnNodeMoveOrCopy;
        }

        if (doMove)
        {
            this.checkAndProcessContentPropertiesMove(targetNodeRef, properties, null);
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected ContentStore selectStoreForContentDataMove(final NodeRef nodeRef, final QName propertyQName, final ContentData contentData,
            final Void customData)
    {
        final Object site = ContentStoreContext.getContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_SITE);
        final Object sitePreset = ContentStoreContext.getContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_SITE_PRESET);

        final ContentStore targetStore;
        if (site != null && this.storeBySite != null && this.storeBySite.containsKey(site))
        {
            targetStore = this.storeBySite.get(site);
        }
        else if (sitePreset != null && this.storeBySitePreset != null && this.storeBySitePreset.containsKey(sitePreset))
        {
            targetStore = this.storeBySite.get(sitePreset);
        }
        else
        {
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
        final Object site = ContentStoreContext.getContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_SITE);
        final Object sitePreset = ContentStoreContext.getContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_SITE_PRESET);

        final ContentStore writeStore;
        if (site != null && this.storeBySite != null && this.storeBySite.containsKey(site))
        {
            LOGGER.debug("Selecting store for site {} to write {}", site, ctx);
            writeStore = this.storeBySite.get(site);
        }
        else if (sitePreset != null && this.storeBySitePreset != null && this.storeBySitePreset.containsKey(sitePreset))
        {
            LOGGER.debug("Selecting store for site preset {} to write {}", sitePreset, ctx);
            writeStore = this.storeBySitePreset.get(sitePreset);
        }
        else
        {
            LOGGER.debug("ContentContext {} cannot be handled - delegating to super.selectWiteStoreFromRoute", ctx);
            writeStore = super.selectWriteStoreFromRoutes(ctx);
        }

        return writeStore;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected List<ContentStore> getStores(final String contentUrl)
    {
        // TODO filter based on protocol
        return this.getAllStores();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean isRoutable(final ContentContext ctx)
    {
        final Object site = ContentStoreContext.getContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_SITE);
        final Object sitePreset = ContentStoreContext.getContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_SITE_PRESET);

        final boolean result = site != null || sitePreset != null;
        return result;
    }

    private void afterPropertiesSet_setupChangePolicies()
    {
        if (this.moveStoresOnNodeMoveOrCopyName != null)
        {
            this.moveStoresOnNodeMoveOrCopyQName = QName.resolveToQName(this.namespaceService, this.moveStoresOnNodeMoveOrCopyName);
            PropertyCheck.mandatory(this, "moveStoresOnChangeOptionPropertyQName", this.moveStoresOnNodeMoveOrCopyQName);

            final PropertyDefinition moveStoresOnChangeOptionPropertyDefinition = this.dictionaryService
                    .getProperty(this.moveStoresOnNodeMoveOrCopyQName);
            if (moveStoresOnChangeOptionPropertyDefinition == null
                    || !DataTypeDefinition.BOOLEAN.equals(moveStoresOnChangeOptionPropertyDefinition.getDataType().getName())
                    || moveStoresOnChangeOptionPropertyDefinition.isMultiValued())
            {
                throw new IllegalStateException(
                        this.moveStoresOnNodeMoveOrCopyName + " is not a valid content model property of type single-valued d:boolean");
            }
        }

        if (this.moveStoresOnNodeMoveOrCopy || this.moveStoresOnNodeMoveOrCopyQName != null)
        {
            PropertyCheck.mandatory(this, "policyComponent", this.policyComponent);
            PropertyCheck.mandatory(this, "dictionaryService", this.dictionaryService);
            PropertyCheck.mandatory(this, "nodeService", this.nodeService);

            this.policyComponent.bindClassBehaviour(OnCopyCompletePolicy.QNAME, ContentModel.TYPE_BASE,
                    new JavaBehaviour(this, "onCopyComplete", NotificationFrequency.EVERY_EVENT));
            this.policyComponent.bindClassBehaviour(OnMoveNodePolicy.QNAME, ContentModel.TYPE_BASE,
                    new JavaBehaviour(this, "onMoveNode", NotificationFrequency.EVERY_EVENT));
        }
    }

    private void afterPropertiesSet_setupStoreData()
    {
        if ((this.storeBySite == null || this.storeBySite.isEmpty())
                && (this.storeBySitePreset == null || this.storeBySitePreset.isEmpty()))
        {
            throw new IllegalStateException("No stores have been defined for sites / site presets");
        }

        if (this.allStores == null)
        {
            this.allStores = new ArrayList<>();
        }

        if (this.storeBySite != null)
        {
            for (final ContentStore store : this.storeBySite.values())
            {
                if (!this.allStores.contains(store))
                {
                    this.allStores.add(store);
                }
            }
        }

        if (this.storeBySitePreset != null)
        {
            for (final ContentStore store : this.storeBySitePreset.values())
            {
                if (!this.allStores.contains(store))
                {
                    this.allStores.add(store);
                }
            }
        }
    }
}
