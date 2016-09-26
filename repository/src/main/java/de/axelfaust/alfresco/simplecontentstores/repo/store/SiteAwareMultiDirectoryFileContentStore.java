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

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.AbstractContentWriter;
import org.alfresco.repo.content.ContentLimitProvider;
import org.alfresco.repo.content.ContentLimitProvider.SimpleFixedLimitProvider;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.content.NodeContentContext;
import org.alfresco.repo.content.UnsupportedContentUrlException;
import org.alfresco.repo.copy.CopyServicePolicies.OnCopyCompletePolicy;
import org.alfresco.repo.node.NodeServicePolicies.OnMoveNodePolicy;
import org.alfresco.repo.policy.Behaviour.NotificationFrequency;
import org.alfresco.repo.policy.JavaBehaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.repo.transaction.TransactionalResourceHelper;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.dictionary.PropertyDefinition;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.EqualsHelper;
import org.alfresco.util.Pair;
import org.alfresco.util.ParameterCheck;
import org.alfresco.util.PropertyCheck;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.axelfaust.alfresco.simplecontentstores.repo.store.context.ContentStoreContext;
import de.axelfaust.alfresco.simplecontentstores.repo.store.context.ContentStoreContext.ContentStoreOperation;
import de.axelfaust.alfresco.simplecontentstores.repo.store.context.ContentStoreContextInitializer;

/**
 * @author Axel Faust
 */
public class SiteAwareMultiDirectoryFileContentStore extends FileContentStore implements OnCopyCompletePolicy, OnMoveNodePolicy
{

    private static final String SITE_PATH_INDICATOR = "_site_/";

    private static final Logger LOGGER = LoggerFactory.getLogger(SiteAwareMultiDirectoryFileContentStore.class);

    protected transient Collection<ContentStoreContextInitializer> contentStoreContextInitializers;

    protected PolicyComponent policyComponent;

    protected NamespaceService namespaceService;

    protected DictionaryService dictionaryService;

    protected NodeService nodeService;

    protected Map<String, String> rootAbsolutePathsBySitePreset;

    protected Map<String, String> rootAbsolutePathsBySite;

    protected Map<String, String> protocolsBySitePreset;

    protected Map<String, String> protocolsBySite;

    protected transient Map<String, File> rootDirectoriesByProtocol = new HashMap<>();

    protected boolean useSiteFolderInGenericDirectories;

    protected boolean moveStoresOnNodeMoveOrCopy;

    protected Map<String, ContentLimitProvider> contentLimitProviderBySitePreset;

    protected Map<String, ContentLimitProvider> contentLimitProviderBySite;

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
        PropertyCheck.mandatory(this, "namespaceService", this.namespaceService);

        this.afterPropertiesSet_setupRootDirectoriesByProtocol();
        this.afterPropertiesSet_setupChangePolicies();
    }

    /**
     * @param policyComponent
     *            the policyComponent to set
     */
    public void setPolicyComponent(final PolicyComponent policyComponent)
    {
        this.policyComponent = policyComponent;
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
     * @param dictionaryService
     *            the dictionaryService to set
     */
    public void setDictionaryService(final DictionaryService dictionaryService)
    {
        this.dictionaryService = dictionaryService;
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
     * @param useSiteFolderInGenericDirectories
     *            the useSiteFolderInGenericDirectories to set
     */
    public void setUseSiteFolderInGenericDirectories(final boolean useSiteFolderInGenericDirectories)
    {
        this.useSiteFolderInGenericDirectories = useSiteFolderInGenericDirectories;
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
     */
    public void setMoveStoresOnNodeMoveOrCopyName(final String moveStoresOnNodeMoveOrCopyName)
    {
        this.moveStoresOnNodeMoveOrCopyName = moveStoresOnNodeMoveOrCopyName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean exists(final String contentUrl)
    {
        ParameterCheck.mandatoryString("contentUrl", contentUrl);
        final String effectiveContentUrl = this.determineEffectiveContentUrl(contentUrl, false);
        final boolean result = super.exists(effectiveContentUrl);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentReader getReader(final String contentUrl)
    {
        ParameterCheck.mandatoryString("contentUrl", contentUrl);

        final String effectiveContentUrl = this.determineEffectiveContentUrl(contentUrl, false);
        final ContentReader reader = super.getReader(effectiveContentUrl);
        return reader;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public boolean delete(final String contentUrl)
    {
        ParameterCheck.mandatoryString("contentUrl", contentUrl);
        final String effectiveContentUrl = this.determineEffectiveContentUrl(contentUrl, false);
        final boolean result = super.delete(effectiveContentUrl);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("deprecation")
    @Override
    public void getUrls(final Date createdAfter, final Date createdBefore, final ContentUrlHandler handler)
    {
        for (final File rootDirectory : this.rootDirectoriesByProtocol.values())
        {
            this.getUrls(rootDirectory, handler, createdAfter, createdBefore);
        }

        LOGGER.debug("Listed all content URLS: \n   store: {}", this);
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

    protected void checkAndProcessContentPropertiesMove(final NodeRef affectedNode)
    {
        final Collection<QName> contentProperties = this.dictionaryService.getAllProperties(DataTypeDefinition.CONTENT);

        // just copied/moved so properties should be cached
        final Map<QName, Serializable> properties = this.nodeService.getProperties(affectedNode);

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
            final Collection<QName> setProperties = new HashSet<>(properties.keySet());
            setProperties.retainAll(contentProperties);

            // only act if node actually has content properties set
            if (!setProperties.isEmpty())
            {
                final Map<QName, Serializable> contentPropertiesMap = new HashMap<>();
                for (final QName contentProperty : setProperties)
                {
                    final Serializable value = properties.get(contentProperty);
                    contentPropertiesMap.put(contentProperty, value);
                }

                if (!contentPropertiesMap.isEmpty())
                {
                    ContentStoreContext.executeInNewContext(new ContentStoreOperation<Void>()
                    {

                        /**
                         * {@inheritDoc}
                         */
                        @Override
                        public Void execute()
                        {
                            SiteAwareMultiDirectoryFileContentStore.this.processContentPropertiesMove(affectedNode, contentPropertiesMap);
                            return null;
                        }
                    });
                }
            }
        }
    }

    protected void processContentPropertiesMove(final NodeRef nodeRef, final Map<QName, Serializable> contentProperties)
    {
        final Map<QName, Serializable> updates = new HashMap<>();
        for (final Entry<QName, Serializable> contentPropertyEntry : contentProperties.entrySet())
        {
            this.processContentPropertyMove(nodeRef, contentPropertyEntry.getKey(), contentPropertyEntry.getValue(), updates);
        }

        if (!updates.isEmpty())
        {
            this.nodeService.addProperties(nodeRef, updates);
        }
    }

    protected void initializeContentStoreContext(final NodeRef nodeRef)
    {
        this.ensureInitializersAreSet();

        // use ContentModel.PROP_CONTENT as a dummy we need for initialization
        final NodeContentContext initializerContext = new NodeContentContext(null, null, nodeRef, ContentModel.PROP_CONTENT);
        for (final ContentStoreContextInitializer initializer : this.contentStoreContextInitializers)
        {
            initializer.initialize(initializerContext);
        }
    }

    protected void processContentPropertyMove(final NodeRef nodeRef, final QName propertyQName, final Serializable value,
            final Map<QName, Serializable> updates)
    {
        if (value instanceof ContentData)
        {
            final ContentData contentData = (ContentData) value;
            final ContentData updatedContentData = this.processContentDataMove(nodeRef, propertyQName, contentData);
            if (updatedContentData != null)
            {
                updates.put(propertyQName, updatedContentData);
            }
        }
        else if (value instanceof Collection<?>)
        {
            final Collection<?> values = (Collection<?>) value;
            final List<Object> updatedValues = new ArrayList<>();
            for (final Object valueElement : values)
            {
                if (valueElement instanceof ContentData)
                {
                    final ContentData updatedContentData = this.processContentDataMove(nodeRef, propertyQName, (ContentData) valueElement);
                    if (updatedContentData != null)
                    {
                        updatedValues.add(updatedContentData);
                    }
                    else
                    {
                        updatedValues.add(valueElement);
                    }
                }
                else
                {
                    updatedValues.add(valueElement);
                }
            }

            if (!EqualsHelper.nullSafeEquals(values, updatedValues))
            {
                updates.put(propertyQName, (Serializable) updatedValues);
            }
        }
    }

    protected ContentData processContentDataMove(final NodeRef nodeRef, final QName propertyQName, final ContentData contentData)
    {
        ContentData updatedContentData = null;
        final String currentUrl = contentData.getContentUrl();
        final String currentProtocol = currentUrl.substring(0, currentUrl.indexOf(PROTOCOL_DELIMITER));

        // no need to act if not stored in any of our directories
        if (this.rootDirectoriesByProtocol.containsKey(currentProtocol))
        {
            this.initializeContentStoreContext(nodeRef);

            final String newContentUrl = this.determineEffectiveContentUrl(currentUrl, true);
            if (!EqualsHelper.nullSafeEquals(currentProtocol, newContentUrl))
            {
                final ContentReader newReader = this.getReader(newContentUrl);
                if (newReader.exists())
                {
                    LOGGER.debug("Updating content data for {} on {} with new content URL {}", propertyQName, nodeRef, newContentUrl);

                    newReader.setMimetype(contentData.getMimetype());
                    newReader.setEncoding(contentData.getEncoding());
                    newReader.setLocale(contentData.getLocale());

                    updatedContentData = newReader.getContentData();
                }
                else
                {
                    LOGGER.debug("Copying content of {} on {} from {} to {}", propertyQName, nodeRef, currentUrl, newContentUrl);

                    final Set<String> urlsToDelete = TransactionalResourceHelper.getSet(StoreConstants.KEY_POST_ROLLBACK_DELETION_URLS);
                    urlsToDelete.add(newContentUrl);

                    final ContentReader reader = this.getReader(currentUrl);
                    final ContentWriter writer = this.getWriterInternal(reader, newContentUrl);
                    writer.putContent(reader);

                    updatedContentData = new ContentData(writer.getContentUrl(), contentData.getMimetype(), contentData.getSize(),
                            contentData.getEncoding(), contentData.getLocale());
                }
            }
            else
            {
                LOGGER.trace("No relevant change in content URL for {} on {}", propertyQName, nodeRef);
            }
        }
        else
        {
            LOGGER.trace("Content data for {} on {} not stored in any directory of this store", propertyQName, nodeRef);
        }

        return updatedContentData;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ContentWriter getWriterInternal(final ContentReader existingContentReader, final String newContentUrl)
    {
        String effectiveNewContentUrl = null;
        if (newContentUrl != null)
        {
            effectiveNewContentUrl = this.determineEffectiveContentUrl(newContentUrl, false);
        }

        final ContentWriter contentWriter = super.getWriterInternal(existingContentReader, effectiveNewContentUrl);

        effectiveNewContentUrl = contentWriter.getContentUrl();
        if (!effectiveNewContentUrl.startsWith(this.protocol + PROTOCOL_DELIMITER))
        {
            final Object site = ContentStoreContext.getContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_SITE);
            final Object sitePreset = ContentStoreContext.getContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_SITE_PRESET);

            if (site != null || sitePreset != null)
            {
                ContentLimitProvider provider;

                provider = this.contentLimitProviderBySite != null ? this.contentLimitProviderBySite.get(site) : null;
                provider = provider == null && this.contentLimitProviderBySitePreset != null
                        ? this.contentLimitProviderBySitePreset.get(site) : null;

                if (provider != null && contentWriter instanceof AbstractContentWriter)
                {
                    ((AbstractContentWriter) contentWriter).setContentLimitProvider(provider);
                }
            }
        }

        return contentWriter;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected String createNewFileStoreUrl()
    {
        final String baseNewContentUrl = super.createNewFileStoreUrl();
        final String effectiveNewContentUrl = this.determineEffectiveContentUrl(baseNewContentUrl, true);
        return effectiveNewContentUrl;
    }

    /**
     *
     * {@inheritDoc}
     */
    // only needed for deprecated getUrls
    @Deprecated
    @Override
    protected String makeContentUrl(final File file)
    {
        final String path = file.getAbsolutePath();

        final List<Entry<String, String>> absoluteRootPaths = new ArrayList<>();
        absoluteRootPaths.addAll(this.rootAbsolutePathsBySitePreset.entrySet());
        absoluteRootPaths.addAll(this.rootAbsolutePathsBySite.entrySet());

        // check if it belongs to this store
        String protocol = null;
        int absolutePathLength = -1;
        if (path.startsWith(this.rootAbsolutePath))
        {
            // the path may contain a site name - since this operation is deprecated we are NOT converting them into informational URL path
            // elements
            absolutePathLength = this.rootAbsolutePath.length();
            protocol = this.protocol;
        }
        else
        {
            for (final Entry<String, String> rootAbsolutePathEntry : this.rootAbsolutePathsBySitePreset.entrySet())
            {
                // the paths may contain a site name - since this operation is deprecated we are NOT converting them into informational URL
                // path elements
                if (path.startsWith(rootAbsolutePathEntry.getValue()))
                {
                    absolutePathLength = rootAbsolutePathEntry.getValue().length();
                    protocol = this.protocolsBySitePreset.get(rootAbsolutePathEntry.getKey());
                    break;
                }
            }

            if (protocol == null)
            {
                for (final Entry<String, String> rootAbsolutePathEntry : this.rootAbsolutePathsBySite.entrySet())
                {
                    if (path.startsWith(rootAbsolutePathEntry.getValue()))
                    {
                        absolutePathLength = rootAbsolutePathEntry.getValue().length();
                        protocol = this.protocolsBySite.get(rootAbsolutePathEntry.getKey());
                        break;
                    }
                }
            }
        }

        if (protocol == null)
        {
            throw new AlfrescoRuntimeException(
                    "File does not fall below the store's root: \n" + "   file: " + file + "\n" + "   store: " + this);
        }

        // strip off the file separator char, if present
        int index = absolutePathLength;
        if (path.charAt(index) == File.separatorChar)
        {
            index++;
        }

        // strip off the root path and adds the protocol prefix
        String url = protocol + ContentStore.PROTOCOL_DELIMITER + path.substring(index);
        // replace '\' with '/' so that URLs are consistent across all filesystems
        url = url.replace('\\', '/');
        // done
        return url;
    }

    @Override
    protected File makeFile(final String contentUrl)
    {
        final String baseContentUrl = ContentUrlUtils.getBaseContentUrl(contentUrl);
        final Pair<String, String> urlParts = this.getContentUrlParts(baseContentUrl);
        final String protocol = urlParts.getFirst();
        String relativePath = urlParts.getSecond();

        if (this.useSiteFolderInGenericDirectories)
        {
            final List<String> prefixes = ContentUrlUtils.extractPrefixes(contentUrl);
            final int indexSiteIndicator = prefixes.indexOf(SITE_PATH_INDICATOR);
            if (indexSiteIndicator != -1 && prefixes.size() > indexSiteIndicator + 1)
            {
                final String site = prefixes.get(indexSiteIndicator + 1);
                relativePath = site + "/" + relativePath;
            }
        }

        return this.makeFile(protocol, relativePath);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected File makeFile(final String protocol, final String relativePath)
    {
        // Check the protocol
        if (!StoreConstants.WILDCARD_PROTOCOL.equals(protocol) && !this.rootDirectoriesByProtocol.containsKey(protocol))
        {
            throw new UnsupportedContentUrlException(this, protocol + PROTOCOL_DELIMITER + relativePath);
        }

        // get the file
        File rootDirectory = this.rootDirectoriesByProtocol.get(protocol);
        if (rootDirectory == null)
        {
            rootDirectory = this.rootDirectory;
        }
        final File file = new File(rootDirectory, relativePath);

        this.ensureFileInContentStore(file);

        // done
        return file;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void ensureFileInContentStore(final File file)
    {
        boolean contained = false;

        final String fileNormalizedAbsoultePath = FilenameUtils.normalize(file.getAbsolutePath());
        final List<String> absoluteRootPaths = new ArrayList<>();
        absoluteRootPaths.add(this.rootAbsolutePath);
        absoluteRootPaths.addAll(this.rootAbsolutePathsBySitePreset.values());
        absoluteRootPaths.addAll(this.rootAbsolutePathsBySite.values());

        for (final String rootAbsolutePath : absoluteRootPaths)
        {
            final String rootNormalizedAbsolutePath = FilenameUtils.normalize(rootAbsolutePath);
            contained = fileNormalizedAbsoultePath.startsWith(rootNormalizedAbsolutePath);

            if (contained)
            {
                break;
            }
        }

        if (!contained)
        {
            throw new ContentIOException("Access to files outside of content store root is not allowed: " + file);
        }
    }

    protected String determineEffectiveContentUrl(final String baseContentUrl, final boolean allowProtocolOverride)
    {
        String effectiveContentUrl = baseContentUrl;

        final Object site = ContentStoreContext.getContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_SITE);
        final Object sitePreset = ContentStoreContext.getContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_SITE_PRESET);

        final Pair<String, String> urlParts = this.getContentUrlParts(baseContentUrl);
        final String protocol = urlParts.getFirst();

        if ((StoreConstants.WILDCARD_PROTOCOL.equals(protocol) || allowProtocolOverride) && (site != null || sitePreset != null))
        {
            LOGGER.debug("Determining effective content URL for base URL {} and context attributes site {} and site preset {}",
                    baseContentUrl, site, sitePreset);

            final String normalizedContentUrl = ContentUrlUtils.getBaseContentUrl(baseContentUrl);
            if (!normalizedContentUrl.equals(baseContentUrl))
            {
                LOGGER.debug("Normalized base URL {} to {}", baseContentUrl, normalizedContentUrl);
            }

            String effectiveProtocol = null;
            boolean genericDirectory = false;

            if (site != null && this.protocolsBySite != null)
            {
                effectiveProtocol = this.protocolsBySite.get(site);
            }

            if (effectiveProtocol == null && sitePreset != null && this.protocolsBySitePreset != null)
            {
                effectiveProtocol = this.protocolsBySitePreset.get(sitePreset);
                genericDirectory = true;
            }

            if (effectiveProtocol == null)
            {
                effectiveProtocol = this.protocol;
                genericDirectory = true;
            }

            final StringBuilder stringBuilder = new StringBuilder(normalizedContentUrl.length() * 2);
            stringBuilder.append(effectiveProtocol);
            stringBuilder.append(PROTOCOL_DELIMITER);
            stringBuilder.append(urlParts.getSecond());
            effectiveContentUrl = stringBuilder.toString();

            if (site != null && genericDirectory && this.useSiteFolderInGenericDirectories)
            {
                effectiveContentUrl = ContentUrlUtils.getContentUrlWithPrefixes(effectiveContentUrl, SITE_PATH_INDICATOR,
                        String.valueOf(site));
            }

            LOGGER.debug("Determined effective content URL {} for base URL {}, and context attributes site {} and site preset {}",
                    effectiveContentUrl, baseContentUrl, site, sitePreset);
        }
        return effectiveContentUrl;
    }

    protected void ensureInitializersAreSet()
    {
        if (this.contentStoreContextInitializers == null)
        {
            synchronized (this)
            {
                if (this.contentStoreContextInitializers == null)
                {
                    this.contentStoreContextInitializers = this.applicationContext
                            .getBeansOfType(ContentStoreContextInitializer.class, false, false).values();
                }
            }
        }
    }

    protected void afterPropertiesSet_setupRootDirectoriesByProtocol()
    {
        if (this.rootAbsolutePathsBySite != null)
        {
            PropertyCheck.mandatory(this, "protocolBySite", this.protocolsBySite);

            for (final Entry<String, String> entry : this.rootAbsolutePathsBySite.entrySet())
            {
                final String site = entry.getKey();
                final String protocol = this.protocolsBySite.get(site);
                PropertyCheck.mandatory(this, "protocolBySite." + site, protocol);

                if (this.rootDirectoriesByProtocol.containsKey(protocol))
                {
                    throw new ContentIOException("Failed to set up site aware content store - duplicate protocol: " + protocol, null);
                }

                final File directory = new File(entry.getValue());
                if (!directory.exists() && !directory.mkdirs())
                {
                    throw new ContentIOException("Failed to create store root: " + directory, null);
                }

                this.rootDirectoriesByProtocol.put(protocol, directory);
                entry.setValue(directory.getAbsolutePath());
            }
        }
        else
        {
            this.rootAbsolutePathsBySite = Collections.emptyMap();
        }

        if (this.rootAbsolutePathsBySitePreset != null)
        {
            PropertyCheck.mandatory(this, "protocolBySitePreset", this.protocolsBySitePreset);

            for (final Entry<String, String> entry : this.rootAbsolutePathsBySitePreset.entrySet())
            {
                final String sitePreset = entry.getKey();
                final String protocol = this.protocolsBySitePreset.get(sitePreset);
                PropertyCheck.mandatory(this, "protocolBySitePreset." + sitePreset, protocol);

                if (this.rootDirectoriesByProtocol.containsKey(protocol))
                {
                    throw new ContentIOException("Failed to set up site aware content store - duplicate protocol: " + protocol, null);
                }

                final File directory = new File(entry.getValue());
                if (!directory.exists() && !directory.mkdirs())
                {
                    throw new ContentIOException("Failed to create store root: " + directory, null);
                }

                this.rootDirectoriesByProtocol.put(protocol, directory);
                entry.setValue(directory.getAbsolutePath());
            }
        }
        else
        {
            this.rootAbsolutePathsBySitePreset = Collections.emptyMap();
        }

        if (this.rootDirectoriesByProtocol.containsKey(this.protocol))
        {
            throw new ContentIOException("Failed to set up site aware content store - duplicate protocol: " + this.protocol, null);
        }

        this.rootDirectoriesByProtocol.put(this.protocol, this.rootDirectory);
    }

    protected void afterPropertiesSet_setupChangePolicies()
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
}
