/*
 * Copyright 2017 - 2020 Acosix GmbH
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
import org.alfresco.service.cmr.repository.datatype.DefaultTypeConverter;
import org.alfresco.service.cmr.site.SiteService;
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
     * @param moveStoresOnNodeMoveOrCopyOverridePropertyName
     *            the moveStoresOnNodeMoveOrCopyOverridePropertyName to set
     */
    public void setMoveStoresOnNodeMoveOrCopyOverridePropertyName(final String moveStoresOnNodeMoveOrCopyOverridePropertyName)
    {
        this.moveStoresOnNodeMoveOrCopyOverridePropertyName = moveStoresOnNodeMoveOrCopyOverridePropertyName;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public boolean isContentUrlSupported(final String contentUrl)
    {
        // optimisation: check the likely candidate store based on context first
        final ContentStore storeForCurrentContext = this.selectStoreForCurrentContext();

        boolean supported = false;
        if (storeForCurrentContext != null)
        {
            LOGGER.debug("Preferentially using store for current context to check support for content URL {}", contentUrl);
            supported = storeForCurrentContext.isContentUrlSupported(contentUrl);
        }

        if (!supported)
        {
            LOGGER.debug("Delegating to super implementation to check support for content URL {}", contentUrl);
            supported = super.isContentUrlSupported(contentUrl);
        }
        return supported;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public boolean isWriteSupported()
    {
        // optimisation: check the likely candidate store based on context first
        final ContentStore storeForCurrentContext = this.selectStoreForCurrentContext();

        boolean supported = false;
        if (storeForCurrentContext != null)
        {
            LOGGER.debug("Preferentially using store for current context to check write suport");
            supported = storeForCurrentContext.isWriteSupported();
        }

        if (!supported)
        {
            LOGGER.debug("Delegating to super implementation to check write support");
            supported = super.isWriteSupported();
        }
        return supported;
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
            LOGGER.debug("Processing onMoveNode for {} from {} to {}", movedNode, oldChildAssocRef, newChildAssocRef);

            // check for actual move-relevant site move
            final Boolean moveRelevant = AuthenticationUtil.runAsSystem(() -> {
                final NodeRef sourceSite = this.resolveSiteForNode(oldParent);
                final NodeRef targetSite = this.resolveSiteForNode(newParent);

                ContentStore sourceStore = this.resolveStoreForSite(sourceSite);
                sourceStore = sourceStore != null ? sourceStore : this.fallbackStore;
                ContentStore targetStore = this.resolveStoreForSite(targetSite);
                targetStore = targetStore != null ? targetStore : this.fallbackStore;

                final boolean differentStores = sourceStore != targetStore;
                return Boolean.valueOf(differentStores);
            });

            if (Boolean.TRUE.equals(moveRelevant))
            {
                LOGGER.debug("Node {} was moved to a location for which content should be stored in a different store", movedNode);
                this.checkAndProcessContentPropertiesMove(movedNode);
            }
            else
            {
                LOGGER.debug("Node {} was not moved into a location for which content should be stored in a different store", movedNode);
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
            LOGGER.debug("Processing onCopyComplete for copy from {} to {}", sourceNodeRef, targetNodeRef);

            // check for actual move-relevant site copy
            final Boolean moveRelevant = AuthenticationUtil.runAsSystem(() -> {
                final NodeRef sourceSite = this.resolveSiteForNode(sourceNodeRef);
                final NodeRef targetSite = this.resolveSiteForNode(targetNodeRef);

                ContentStore sourceStore = this.resolveStoreForSite(sourceSite);
                sourceStore = sourceStore != null ? sourceStore : this.fallbackStore;
                ContentStore targetStore = this.resolveStoreForSite(targetSite);
                targetStore = targetStore != null ? targetStore : this.fallbackStore;

                final boolean differentStores = sourceStore != targetStore;
                return Boolean.valueOf(differentStores);
            });

            if (Boolean.TRUE.equals(moveRelevant))
            {
                LOGGER.debug("Node {} was copied into a location for which content should be stored in a different store", targetNodeRef);
                this.checkAndProcessContentPropertiesMove(targetNodeRef);
            }
            else
            {
                LOGGER.debug("Node {} was not copied into a location for which content should be stored in a different store",
                        targetNodeRef);
            }
        }
    }

    protected void checkAndProcessContentPropertiesMove(final NodeRef affectedNode)
    {
        this.checkAndProcessContentPropertiesMove(affectedNode, this.moveStoresOnNodeMoveOrCopy,
                this.moveStoresOnNodeMoveOrCopyOverridePropertyQName, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ContentStore selectStore(final String contentUrl, final boolean mustExist)
    {
        // optimisation: check the likely candidate store based on context first
        final ContentStore storeForCurrentContext = this.selectStoreForCurrentContext();

        ContentStore store = null;
        if (storeForCurrentContext != null)
        {
            LOGGER.debug(
                    "Preferentially testing store for current context to select store for read of content URL {} with mustExist flag of {}",
                    contentUrl, mustExist);
            if (!mustExist || (storeForCurrentContext.isContentUrlSupported(contentUrl) && storeForCurrentContext.exists(contentUrl)))
            {
                store = storeForCurrentContext;
            }
        }

        if (store == null)
        {
            LOGGER.debug("Delegating to super implementation to select store for read of content URL {} with mustExist flag of {}",
                    contentUrl, mustExist);
            store = super.selectStore(contentUrl, mustExist);
        }
        return store;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected ContentStore selectStoreForContentDataMove(final NodeRef nodeRef, final QName propertyQName, final ContentData contentData,
            final Void customData)
    {
        ContentStore targetStore = this.selectStoreForCurrentContext();
        if (targetStore == null)
        {
            LOGGER.debug(
                    "Store-specific logic could not select a store to move {} in current context - delegating to super.selectStoreForContentDataMove",
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
        ContentStore writeStore = this.selectStoreForCurrentContext();
        if (writeStore == null)
        {
            LOGGER.debug(
                    "Store-specific logic could not select a write store for current context - delegating to super.selectWiteStoreFromRoute",
                    ctx);
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

    protected ContentStore selectStoreForCurrentContext()
    {
        final String site = DefaultTypeConverter.INSTANCE.convert(String.class,
                ContentStoreContext.getContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_SITE));
        final String sitePreset = DefaultTypeConverter.INSTANCE.convert(String.class,
                ContentStoreContext.getContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_SITE_PRESET));

        return this.resolveStoreForSite(site, sitePreset);
    }

    /**
     * Resolves the content store to use for a particular site.
     *
     * @param siteNode
     *            the node representing the site - may be {@code null}
     * @return the content store to use for the site - can be {@code null} if this implementation layer cannot determine the store on its
     *         own, and selection of the {@code fallbackStore} by the super implementation has to be presumed
     */
    protected ContentStore resolveStoreForSite(final NodeRef siteNode)
    {
        String site = null;
        String sitePreset = null;

        if (siteNode != null)
        {
            final Map<QName, Serializable> properties = this.nodeService.getProperties(siteNode);
            site = DefaultTypeConverter.INSTANCE.convert(String.class, properties.get(ContentModel.PROP_NAME));
            sitePreset = DefaultTypeConverter.INSTANCE.convert(String.class, properties.get(SiteModel.PROP_SITE_PRESET));
        }

        return this.resolveStoreForSite(site, sitePreset);
    }

    /**
     * Resolves the content store to use for a particular site.
     *
     * @param site
     *            the short name of the site
     * @param sitePreset
     *            the preset of the site
     * @return the content store to use for the site - can be {@code null} if this implementation layer cannot determine the store on its
     *         own, and selection of the {@code fallbackStore} by the super implementation has to be presumed
     */
    protected ContentStore resolveStoreForSite(final String site, final String sitePreset)
    {
        LOGGER.debug("Resolving store for site {} and preset {}", site, sitePreset);

        final ContentStore targetStore;
        if (this.storeBySite != null && site != null && this.storeBySite.containsKey(site))
        {
            targetStore = this.storeBySite.get(site);
        }
        else if (this.storeBySitePreset != null && sitePreset != null && this.storeBySitePreset.containsKey(sitePreset))
        {
            targetStore = this.storeBySitePreset.get(sitePreset);
        }
        else
        {
            targetStore = null;
        }

        LOGGER.debug("Resolved store {}", targetStore);
        return targetStore;
    }

    /**
     * This internal method only exists to avoid the circular dependency we would create when requiring the {@link SiteService} as a
     * dependency for {@link SiteService#getSite(NodeRef) resolving the site of a node}.
     *
     * @param node
     *            the node for which to resolve the site
     * @return the node reference for the site, or {@code null} if the node is not contained in a site via a graph of primary parent
     *         associations
     */
    protected NodeRef resolveSiteForNode(final NodeRef node)
    {
        NodeRef site = null;
        NodeRef curParent = node;
        while (curParent != null)
        {
            final QName curParentType = this.nodeService.getType(curParent);
            if (this.dictionaryService.isSubClass(curParentType, SiteModel.TYPE_SITE))
            {
                site = curParent;
                break;
            }
            curParent = this.nodeService.getPrimaryParent(curParent).getParentRef();
        }
        return site;
    }

    protected void afterPropertiesSet_setupChangePolicies()
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

    protected void afterPropertiesSet_setupStoreData()
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
