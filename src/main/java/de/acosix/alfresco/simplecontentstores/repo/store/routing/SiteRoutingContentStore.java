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
package de.acosix.alfresco.simplecontentstores.repo.store.routing;

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
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.site.SiteModel;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.dictionary.PropertyDefinition;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.EqualsHelper;
import org.alfresco.util.PropertyCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.acosix.alfresco.simplecontentstores.repo.store.context.ContentStoreContext;

/**
 * @author Axel Faust
 */
public class SiteRoutingContentStore extends PropertyRestrictableRoutingContentStore<Void> implements OnCopyCompletePolicy, OnMoveNodePolicy
{

    private static final Logger LOGGER = LoggerFactory.getLogger(SiteRoutingContentStore.class);

    protected Map<String, ContentStore> storeBySitePreset;

    protected Map<String, ContentStore> storeBySite;

    protected boolean moveStoresOnNodeMoveOrCopy;

    protected String moveStoresOnNodeMoveOrCopyOverridePropertyName;

    protected transient QName moveStoresOnNodeMoveOrCopyOverridePropertyQName;

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
     * @deprecated Only exists for backwards compatibility with existing configuration. Use
     *             {@link #setMoveStoresOnNodeMoveOrCopyOverridePropertyName(String)} instead. Will be removed before any proper release.
     */
    @Deprecated
    public void setMoveStoresOnNodeMoveOrCopyName(final String moveStoresOnNodeMoveOrCopyName)
    {
        this.setMoveStoresOnNodeMoveOrCopyOverridePropertyName(moveStoresOnNodeMoveOrCopyName);
    }

    /**
     * @param moveStoresOnNodeMoveOrCopyOverridePropertyName
     *            the moveStoresOnNodeMoveOrCopyOverridePropertyName to set
     */
    public void setMoveStoresOnNodeMoveOrCopyOverridePropertyName(final String moveStoresOnNodeMoveOrCopyOverridePropertyName)
    {
        this.moveStoresOnNodeMoveOrCopyOverridePropertyName = moveStoresOnNodeMoveOrCopyOverridePropertyName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onMoveNode(final ChildAssociationRef oldChildAssocRef, final ChildAssociationRef newChildAssocRef)
    {
        // only act on active nodes which can actually be in a site
        final NodeRef movedNode = oldChildAssocRef.getChildRef();
        final NodeRef oldParent = oldChildAssocRef.getParentRef();
        final NodeRef newParent = newChildAssocRef.getParentRef();
        if (StoreRef.STORE_REF_WORKSPACE_SPACESSTORE.equals(movedNode.getStoreRef()) && !EqualsHelper.nullSafeEquals(oldParent, newParent))
        {
            // check for actual site move
            // can't use siteService without creating circular dependency graph
            // resolve all ancestors via old parent (up until site) and cross-check with ancestors of new parent
            // run as system to avoid performance overhead + issues with intermediary node access restrictions
            final Boolean sameSiteOrBothGlobal = AuthenticationUtil.runAsSystem(() -> {
                final List<NodeRef> oldAncestors = new ArrayList<>();
                NodeRef curParent = oldParent;
                while (curParent != null)
                {
                    oldAncestors.add(curParent);
                    final QName curParentType = this.nodeService.getType(curParent);
                    if (this.dictionaryService.isSubClass(curParentType, SiteModel.TYPE_SITE))
                    {
                        break;
                    }
                    curParent = this.nodeService.getPrimaryParent(curParent).getParentRef();
                }

                boolean sameScope = false;
                curParent = newParent;
                while (!sameScope && curParent != null)
                {
                    sameScope = oldAncestors.contains(curParent);
                    curParent = this.nodeService.getPrimaryParent(curParent).getParentRef();
                }

                return Boolean.valueOf(sameScope);
            });

            if (!Boolean.TRUE.equals(sameSiteOrBothGlobal))
            {
                this.checkAndProcessContentPropertiesMove(movedNode);
            }
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
        if (this.moveStoresOnNodeMoveOrCopyOverridePropertyQName != null)
        {
            final Serializable moveStoresOnChangeOptionValue = properties.get(this.moveStoresOnNodeMoveOrCopyOverridePropertyQName);
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
            targetStore = this.storeBySitePreset.get(sitePreset);
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
        if (this.moveStoresOnNodeMoveOrCopyOverridePropertyName != null)
        {
            this.moveStoresOnNodeMoveOrCopyOverridePropertyQName = QName.resolveToQName(this.namespaceService,
                    this.moveStoresOnNodeMoveOrCopyOverridePropertyName);
            PropertyCheck.mandatory(this, "moveStoresOnNodeMoveOrCopyOverridePropertyQName",
                    this.moveStoresOnNodeMoveOrCopyOverridePropertyQName);

            final PropertyDefinition moveStoresOnChangeOptionPropertyDefinition = this.dictionaryService
                    .getProperty(this.moveStoresOnNodeMoveOrCopyOverridePropertyQName);
            if (moveStoresOnChangeOptionPropertyDefinition == null
                    || !DataTypeDefinition.BOOLEAN.equals(moveStoresOnChangeOptionPropertyDefinition.getDataType().getName())
                    || moveStoresOnChangeOptionPropertyDefinition.isMultiValued())
            {
                throw new IllegalStateException(this.moveStoresOnNodeMoveOrCopyOverridePropertyName
                        + " is not a valid content model property of type single-valued d:boolean");
            }
        }

        if (this.moveStoresOnNodeMoveOrCopy || this.moveStoresOnNodeMoveOrCopyOverridePropertyQName != null)
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
