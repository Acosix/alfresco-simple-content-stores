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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.ContentContext;
import org.alfresco.repo.content.ContentLimitProvider;
import org.alfresco.repo.content.ContentLimitProvider.NoLimitProvider;
import org.alfresco.repo.content.ContentLimitProvider.SimpleFixedLimitProvider;
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
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.site.SiteService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.EqualsHelper;
import org.alfresco.util.ParameterCheck;
import org.alfresco.util.PropertyCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.acosix.alfresco.simplecontentstores.repo.store.context.ContentStoreContext;
import de.acosix.alfresco.simplecontentstores.repo.store.routing.MoveCapableCommonRoutingContentStore;

/**
 * @author Axel Faust
 */
public class SiteRoutingFileContentStore extends MoveCapableCommonRoutingContentStore<Void>
        implements OnCopyCompletePolicy, OnMoveNodePolicy
{

    private static final Logger LOGGER = LoggerFactory.getLogger(SiteRoutingFileContentStore.class);

    protected NamespaceService namespaceService;

    protected String rootAbsolutePath;

    protected Map<String, String> rootAbsolutePathsBySitePreset;

    protected Map<String, String> rootAbsolutePathsBySite;

    protected String protocol = FileContentStore.STORE_PROTOCOL;

    protected Map<String, String> protocolsBySitePreset;

    protected Map<String, String> protocolsBySite;

    protected transient Map<String, ContentStore> storeByProtocol = new HashMap<>();

    protected boolean allowRandomAccess;

    protected boolean readOnly;

    protected boolean deleteEmptyDirs = true;

    protected boolean useSiteFolderInGenericDirectories;

    protected boolean moveStoresOnNodeMoveOrCopy;

    protected ContentLimitProvider contentLimitProvider = new NoLimitProvider();

    protected Map<String, ContentLimitProvider> contentLimitProviderBySitePreset;

    protected Map<String, ContentLimitProvider> contentLimitProviderBySite;

    protected String moveStoresOnNodeMoveOrCopyOverridePropertyName;

    protected transient QName moveStoresOnNodeMoveOrCopyOverridePropertyQName;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "namespaceService", this.namespaceService);

        this.afterPropertiesSet_setupDefaultStore();

        super.afterPropertiesSet();

        this.afterPropertiesSet_setupStoreData();
        this.afterPropertiesSet_setupChangePolicies();
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
     * @param rootAbsolutePath
     *            the rootAbsolutePath to set
     */
    public void setRootAbsolutePath(final String rootAbsolutePath)
    {
        this.rootAbsolutePath = rootAbsolutePath;
    }

    /**
     * Simple alias to {@link #setRootAbsolutePath(String)} for compatibility with previously used {@code FileContentStoreFactoryBean}
     *
     * @param rootDirectory
     *            the rootDirectory to set
     */
    public void setRootDirectory(final String rootDirectory)
    {
        this.rootAbsolutePath = rootDirectory;
    }

    /**
     * @param rootAbsolutePathsBySitePreset
     *            the rootAbsolutePathsBySitePreset to set
     */
    public void setRootAbsolutePathsBySitePreset(final Map<String, String> rootAbsolutePathsBySitePreset)
    {
        this.rootAbsolutePathsBySitePreset = rootAbsolutePathsBySitePreset;
    }

    /**
     * @param rootAbsolutePathsBySite
     *            the rootAbsolutePathsBySite to set
     */
    public void setRootAbsolutePathsBySite(final Map<String, String> rootAbsolutePathsBySite)
    {
        this.rootAbsolutePathsBySite = rootAbsolutePathsBySite;
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
     * @param protocolsBySitePreset
     *            the protocolsBySitePreset to set
     */
    public void setProtocolsBySitePreset(final Map<String, String> protocolsBySitePreset)
    {
        this.protocolsBySitePreset = protocolsBySitePreset;
    }

    /**
     * @param protocolsBySite
     *            the protocolsBySite to set
     */
    public void setProtocolsBySite(final Map<String, String> protocolsBySite)
    {
        this.protocolsBySite = protocolsBySite;
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
     * @param useSiteFolderInGenericDirectories
     *            the useSiteFolderInGenericDirectories to set
     */
    public void setUseSiteFolderInGenericDirectories(final boolean useSiteFolderInGenericDirectories)
    {
        this.useSiteFolderInGenericDirectories = useSiteFolderInGenericDirectories;
    }

    /**
     * @param contentLimitProvider
     *            the contentLimitProvider to set
     *
     */
    public void setContentLimitProvider(final ContentLimitProvider contentLimitProvider)
    {
        this.contentLimitProvider = contentLimitProvider;
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
     * @param contentLimitProviderBySitePreset
     *            the contentLimitProviderBySitePreset to set
     */
    public void setContentLimitProviderBySitePreset(final Map<String, ContentLimitProvider> contentLimitProviderBySitePreset)
    {
        this.contentLimitProviderBySitePreset = contentLimitProviderBySitePreset;
    }

    /**
     *
     * @param limits
     *            the fixed limits to set
     */
    public void setFixedLimitBySitePreset(final Map<String, Long> limits)
    {
        ParameterCheck.mandatory("limits", limits);

        if (this.contentLimitProviderBySitePreset == null)
        {
            this.contentLimitProviderBySitePreset = new HashMap<>();
        }

        for (final Entry<String, Long> limitEntry : limits.entrySet())
        {
            final long limit = limitEntry.getValue().longValue();
            if (limit < 0 && limit != ContentLimitProvider.NO_LIMIT)
            {
                throw new IllegalArgumentException("fixedLimit must be non-negative");
            }
            this.contentLimitProviderBySitePreset.put(limitEntry.getKey(), new SimpleFixedLimitProvider(limit));
        }
    }

    /**
     * @param contentLimitProviderBySite
     *            the contentLimitProviderBySite to set
     */
    public void setContentLimitProviderBySite(final Map<String, ContentLimitProvider> contentLimitProviderBySite)
    {
        this.contentLimitProviderBySite = contentLimitProviderBySite;
    }

    /**
     *
     * @param limits
     *            the fixed limits to set
     */
    public void setFixedLimitBySite(final Map<String, Long> limits)
    {
        ParameterCheck.mandatory("limits", limits);

        if (this.contentLimitProviderBySite == null)
        {
            this.contentLimitProviderBySite = new HashMap<>();
        }

        for (final Entry<String, Long> limitEntry : limits.entrySet())
        {
            final long limit = limitEntry.getValue().longValue();
            if (limit < 0 && limit != ContentLimitProvider.NO_LIMIT)
            {
                throw new IllegalArgumentException("fixedLimit must be non-negative");
            }
            this.contentLimitProviderBySite.put(limitEntry.getKey(), new SimpleFixedLimitProvider(limit));
        }
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
        // only act on active nodes which can actually be in a site
        final NodeRef movedNode = oldChildAssocRef.getChildRef();
        final NodeRef oldParent = oldChildAssocRef.getParentRef();
        final NodeRef newParent = newChildAssocRef.getParentRef();
        if (StoreRef.STORE_REF_WORKSPACE_SPACESSTORE.equals(movedNode.getStoreRef()) && !EqualsHelper.nullSafeEquals(oldParent, newParent))
        {
            LOGGER.debug("Processing onMoveNode for {} from {} to {}", movedNode, oldChildAssocRef, newChildAssocRef);

            // check for actual site move
            final Boolean sameSiteOrBothGlobal = AuthenticationUtil.runAsSystem(() -> {
                final NodeRef sourceSite = this.resolveSiteForNode(oldParent);
                final NodeRef targetSite = this.resolveSiteForNode(newParent);

                final boolean sameSiteOrBothGlobal_ = EqualsHelper.nullSafeEquals(sourceSite, targetSite);
                return Boolean.valueOf(sameSiteOrBothGlobal_);
            });

            if (!Boolean.TRUE.equals(sameSiteOrBothGlobal))
            {
                LOGGER.debug("Node {} was moved into a different site context", movedNode);
                this.checkAndProcessContentPropertiesMove(movedNode);
            }
            else
            {
                LOGGER.debug("Node {} was not moved into a different site context", movedNode);
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

            // check for actual inter-site copy
            final Boolean sameSiteOrBothGlobal = AuthenticationUtil.runAsSystem(() -> {
                final NodeRef sourceSite = this.resolveSiteForNode(sourceNodeRef);
                final NodeRef targetSite = this.resolveSiteForNode(targetNodeRef);

                final boolean sameSiteOrBothGlobal_ = EqualsHelper.nullSafeEquals(sourceSite, targetSite);
                return Boolean.valueOf(sameSiteOrBothGlobal_);
            });

            if (!Boolean.TRUE.equals(sameSiteOrBothGlobal))
            {
                LOGGER.debug("Node {} was copied into a different site context", targetNodeRef);
                this.checkAndProcessContentPropertiesMove(targetNodeRef);
            }
            else
            {
                LOGGER.debug("Node {} was not copied into a different site context", targetNodeRef);
            }
        }
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

    protected void checkAndProcessContentPropertiesMove(final NodeRef affectedNode)
    {
        this.checkAndProcessContentPropertiesMove(affectedNode, this.moveStoresOnNodeMoveOrCopy,
                this.moveStoresOnNodeMoveOrCopyOverridePropertyQName, null);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected ContentStore selectStoreForContentDataMove(final NodeRef nodeRef, final QName propertyQName, final ContentData contentData,
            final Void customData)
    {
        final ContentStore writeStore = this.selectStoreForCurrentContext();
        return writeStore;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected ContentStore selectWriteStoreFromRoutes(final ContentContext ctx)
    {
        final ContentStore writeStore = this.selectStoreForCurrentContext();
        return writeStore;
    }

    protected ContentStore selectStoreForCurrentContext()
    {
        final Object site = ContentStoreContext.getContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_SITE);
        final Object sitePreset = ContentStoreContext.getContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_SITE_PRESET);

        final String protocol;
        if (this.protocolsBySite != null && this.protocolsBySite.containsKey(site))
        {
            LOGGER.debug("Selecting store for site {}", site);
            protocol = this.protocolsBySite.get(site);
        }
        else if (this.protocolsBySitePreset != null && this.protocolsBySitePreset.containsKey(sitePreset))
        {
            LOGGER.debug("Selecting store for site preset {}", sitePreset);
            protocol = this.protocolsBySitePreset.get(sitePreset);
        }
        else
        {
            LOGGER.debug("Selecting default store");
            protocol = this.protocol;
        }

        final ContentStore targetStore = this.storeByProtocol.get(protocol);
        LOGGER.debug("Selected store {}", targetStore);
        return targetStore;
    }

    protected void afterPropertiesSet_setupDefaultStore()
    {
        PropertyCheck.mandatory(this, "rootAbsolutePath", this.rootAbsolutePath);
        PropertyCheck.mandatory(this, "protocol", this.protocol);

        if (this.allStores == null)
        {
            this.allStores = new ArrayList<>();
        }

        final SiteAwareFileContentStore defaultFileContentStore = new SiteAwareFileContentStore();
        defaultFileContentStore.setProtocol(this.protocol);
        defaultFileContentStore.setRootAbsolutePath(this.rootAbsolutePath);
        defaultFileContentStore.setApplicationContext(this.applicationContext);
        defaultFileContentStore.setContentLimitProvider(this.contentLimitProvider);
        defaultFileContentStore.setAllowRandomAccess(this.allowRandomAccess);
        defaultFileContentStore.setDeleteEmptyDirs(this.deleteEmptyDirs);
        defaultFileContentStore.setReadOnly(this.readOnly);
        defaultFileContentStore.setUseSiteFolderInGenericDirectories(this.useSiteFolderInGenericDirectories);

        this.storeByProtocol.put(this.protocol, defaultFileContentStore);
        this.allStores.add(defaultFileContentStore);
        this.fallbackStore = defaultFileContentStore;

        defaultFileContentStore.afterPropertiesSet();
    }

    protected void afterPropertiesSet_setupStoreData()
    {
        if (this.rootAbsolutePathsBySite != null)
        {
            PropertyCheck.mandatory(this, "protocolBySite", this.protocolsBySite);

            for (final Entry<String, String> entry : this.rootAbsolutePathsBySite.entrySet())
            {
                final String site = entry.getKey();
                final String protocol = this.protocolsBySite.get(site);
                PropertyCheck.mandatory(this, "protocolBySite." + site, protocol);

                if (this.storeByProtocol.containsKey(protocol))
                {
                    throw new ContentIOException("Failed to set up site aware content store - duplicate protocol: " + protocol, null);
                }

                final SiteAwareFileContentStore siteAwareFileContentStore = new SiteAwareFileContentStore();
                siteAwareFileContentStore.setProtocol(protocol);
                siteAwareFileContentStore.setRootAbsolutePath(entry.getValue());
                siteAwareFileContentStore.setApplicationContext(this.applicationContext);

                ContentLimitProvider contentLimitProvider = null;
                if (this.contentLimitProviderBySite != null)
                {
                    contentLimitProvider = this.contentLimitProviderBySite.get(site);
                }
                if (contentLimitProvider == null)
                {
                    contentLimitProvider = this.contentLimitProvider;
                }
                siteAwareFileContentStore.setContentLimitProvider(contentLimitProvider);

                siteAwareFileContentStore.setAllowRandomAccess(this.allowRandomAccess);
                siteAwareFileContentStore.setDeleteEmptyDirs(this.deleteEmptyDirs);
                siteAwareFileContentStore.setReadOnly(this.readOnly);
                siteAwareFileContentStore.setExtendedEventParameters(Collections.<String, Serializable> singletonMap("Site", site));

                this.storeByProtocol.put(protocol, siteAwareFileContentStore);
                this.allStores.add(siteAwareFileContentStore);

                siteAwareFileContentStore.afterPropertiesSet();
            }

            if (!this.rootAbsolutePathsBySite.keySet().containsAll(this.protocolsBySite.keySet()))
            {
                throw new IllegalStateException(
                        "Invalid store configuration: 'protocolsBySite' specifies protocols for more sites than 'rootAbsolutePathsBySite' specifies storage locations");
            }
        }
        else if (this.protocolsBySite != null && !this.protocolsBySite.isEmpty())
        {
            throw new IllegalStateException(
                    "Invalid store configuration: 'protocolsBySite' is set without corresponding 'rootAbsolutePathsBySite'");
        }

        if (this.rootAbsolutePathsBySitePreset != null)
        {
            PropertyCheck.mandatory(this, "protocolBySitePreset", this.protocolsBySitePreset);

            for (final Entry<String, String> entry : this.rootAbsolutePathsBySitePreset.entrySet())
            {
                final String sitePreset = entry.getKey();
                final String protocol = this.protocolsBySitePreset.get(sitePreset);
                PropertyCheck.mandatory(this, "protocolBySitePreset." + sitePreset, protocol);

                if (this.storeByProtocol.containsKey(protocol))
                {
                    throw new ContentIOException("Failed to set up site aware content store - duplicate protocol: " + protocol, null);
                }

                final SiteAwareFileContentStore siteAwareFileContentStore = new SiteAwareFileContentStore();
                siteAwareFileContentStore.setProtocol(protocol);
                siteAwareFileContentStore.setRootAbsolutePath(entry.getValue());
                siteAwareFileContentStore.setApplicationContext(this.applicationContext);

                ContentLimitProvider contentLimitProvider = null;
                if (this.contentLimitProviderBySitePreset != null)
                {
                    contentLimitProvider = this.contentLimitProviderBySitePreset.get(sitePreset);
                }
                if (contentLimitProvider == null)
                {
                    contentLimitProvider = this.contentLimitProvider;
                }
                siteAwareFileContentStore.setContentLimitProvider(contentLimitProvider);

                siteAwareFileContentStore.setAllowRandomAccess(this.allowRandomAccess);
                siteAwareFileContentStore.setDeleteEmptyDirs(this.deleteEmptyDirs);
                siteAwareFileContentStore.setReadOnly(this.readOnly);
                siteAwareFileContentStore.setUseSiteFolderInGenericDirectories(this.useSiteFolderInGenericDirectories);
                siteAwareFileContentStore
                        .setExtendedEventParameters(Collections.<String, Serializable> singletonMap("SitePreset", sitePreset));

                this.storeByProtocol.put(protocol, siteAwareFileContentStore);
                this.allStores.add(siteAwareFileContentStore);

                siteAwareFileContentStore.afterPropertiesSet();
            }

            if (!this.rootAbsolutePathsBySitePreset.keySet().containsAll(this.protocolsBySitePreset.keySet()))
            {
                throw new IllegalStateException(
                        "Invalid store configuration: 'protocolsBySitePreset' specifies protocols for more sites than 'rootAbsolutePathsBySitePreset' specifies storage locations");
            }
        }
        else if (this.protocolsBySitePreset != null && !this.protocolsBySitePreset.isEmpty())
        {
            throw new IllegalStateException(
                    "Invalid store configuration: 'protocolsBySitePreset' is set without corresponding 'rootAbsolutePathsBySitePreset'");
        }
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
            this.policyComponent.bindClassBehaviour(OnCopyCompletePolicy.QNAME, ContentModel.TYPE_BASE,
                    new JavaBehaviour(this, "onCopyComplete", NotificationFrequency.EVERY_EVENT));
            this.policyComponent.bindClassBehaviour(OnMoveNodePolicy.QNAME, ContentModel.TYPE_BASE,
                    new JavaBehaviour(this, "onMoveNode", NotificationFrequency.EVERY_EVENT));
        }
    }
}
