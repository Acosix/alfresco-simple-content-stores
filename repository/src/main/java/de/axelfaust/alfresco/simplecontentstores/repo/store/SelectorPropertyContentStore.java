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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.content.ContentContext;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.content.NodeContentContext;
import org.alfresco.repo.dictionary.constraint.ConstraintRegistry;
import org.alfresco.repo.dictionary.constraint.ListOfValuesConstraint;
import org.alfresco.repo.node.NodeServicePolicies.BeforeRemoveAspectPolicy;
import org.alfresco.repo.node.NodeServicePolicies.OnAddAspectPolicy;
import org.alfresco.repo.node.NodeServicePolicies.OnUpdatePropertiesPolicy;
import org.alfresco.repo.policy.Behaviour.NotificationFrequency;
import org.alfresco.repo.policy.JavaBehaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.repo.transaction.TransactionalResourceHelper;
import org.alfresco.service.cmr.dictionary.ClassDefinition;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.dictionary.PropertyDefinition;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.datatype.DefaultTypeConverter;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.EqualsHelper;
import org.alfresco.util.Pair;
import org.alfresco.util.PropertyCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import de.axelfaust.alfresco.simplecontentstores.repo.store.context.ContentStoreContext;
import de.axelfaust.alfresco.simplecontentstores.repo.store.context.ContentStoreContext.ContentStoreOperation;
import de.axelfaust.alfresco.simplecontentstores.repo.store.context.ContentStoreContextInitializer;

/**
 * @author Axel Faust
 */
public class SelectorPropertyContentStore extends CommonRoutingContentStore
        implements OnUpdatePropertiesPolicy, OnAddAspectPolicy, BeforeRemoveAspectPolicy, ApplicationContextAware
{

    private static final String KEY_POST_ROLLBACK_DELETION_URLS = "ContentStoreCleaner.PostRollbackDeletionUrls";

    private static final Logger LOGGER = LoggerFactory.getLogger(SelectorPropertyContentStore.class);

    protected ApplicationContext applicationContext;

    protected transient Collection<ContentStoreContextInitializer> contentStoreContextInitializers;

    protected NodeService nodeService;

    protected PolicyComponent policyComponent;

    protected ConstraintRegistry constraintRegistry;

    protected String selectorClassName;

    protected transient QName selectorClassQName;

    protected String selectorPropertyName;

    protected transient QName selectorPropertyQName;

    protected Map<String, ContentStore> storeBySelectorPropertyValue;

    protected transient List<ContentStore> allStores;

    protected boolean moveStoresOnChange;

    protected String moveStoresOnChangeOptionPropertyName;

    protected transient QName moveStoresOnChangeOptionPropertyQName;

    protected String selectorValuesConstraintShortName;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        super.afterPropertiesSet();

        PropertyCheck.mandatory(this, "nodeService", this.nodeService);
        PropertyCheck.mandatory(this, "policyComponent", this.policyComponent);
        PropertyCheck.mandatory(this, "constraintRegistry", this.constraintRegistry);

        this.afterPropertiesSet_validateSelectors();
        this.afterPropertiesSet_setupStoreData();
        this.afterPropertiesSet_setupChangePolicies();
        this.afterPropertiesSet_setupConstraint();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException
    {
        this.applicationContext = applicationContext;
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
     * @param policyComponent
     *            the policyComponent to set
     */
    public void setPolicyComponent(final PolicyComponent policyComponent)
    {
        this.policyComponent = policyComponent;
    }

    /**
     * @param constraintRegistry
     *            the constraintRegistry to set
     */
    public void setConstraintRegistry(final ConstraintRegistry constraintRegistry)
    {
        this.constraintRegistry = constraintRegistry;
    }

    /**
     * @param selectorClassName
     *            the selectorClassName to set
     */
    public void setSelectorClassName(final String selectorClassName)
    {
        this.selectorClassName = selectorClassName;
    }

    /**
     * @param selectorPropertyName
     *            the selectorPropertyName to set
     */
    public void setSelectorPropertyName(final String selectorPropertyName)
    {
        this.selectorPropertyName = selectorPropertyName;
    }

    /**
     * @param storeBySelectorPropertyValue
     *            the storeBySelectorPropertyValue to set
     */
    public void setStoreBySelectorPropertyValue(final Map<String, ContentStore> storeBySelectorPropertyValue)
    {
        this.storeBySelectorPropertyValue = storeBySelectorPropertyValue;
    }

    /**
     * @param selectorValuesConstraintShortName
     *            the selectorValuesConstraintShortName to set
     */
    public void setSelectorValuesConstraintShortName(final String selectorValuesConstraintShortName)
    {
        this.selectorValuesConstraintShortName = selectorValuesConstraintShortName;
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
    public void onAddAspect(final NodeRef nodeRef, final QName aspectQName)
    {
        final Map<QName, Serializable> properties = this.nodeService.getProperties(nodeRef);

        boolean moveIfChanged = false;
        if (this.moveStoresOnChangeOptionPropertyQName != null)
        {
            final Serializable moveStoresOnChangeOptionValue = properties.get(this.moveStoresOnChangeOptionPropertyQName);
            // explicit value wins
            if (moveStoresOnChangeOptionValue != null)
            {
                moveIfChanged = Boolean.TRUE.equals(moveStoresOnChangeOptionValue);
            }
            else
            {
                moveIfChanged = this.moveStoresOnChange;
            }
        }
        else
        {
            moveIfChanged = this.moveStoresOnChange;
        }

        if (moveIfChanged)
        {
            final Serializable selectorValue = properties.get(this.selectorPropertyQName);

            final ContentStore oldStore = this.fallbackStore;
            ContentStore newStore = this.storeBySelectorPropertyValue.get(selectorValue);
            if (newStore == null)
            {
                newStore = this.fallbackStore;
            }

            if (oldStore != newStore || (oldStore == newStore && newStore != this.fallbackStore))
            {
                final Map<QName, Serializable> updates = new HashMap<>();

                final ContentStore targetStore = newStore;
                ContentStoreContext.executeInNewContext(new ContentStoreOperation<Void>()
                {

                    /**
                     * {@inheritDoc}
                     */
                    @Override
                    public Void execute()
                    {
                        SelectorPropertyContentStore.this.processContentPropertiesMove(nodeRef, oldStore, targetStore, updates, properties);
                        return null;
                    }

                });

                if (!updates.isEmpty())
                {
                    this.nodeService.addProperties(nodeRef, updates);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeRemoveAspect(final NodeRef nodeRef, final QName aspectQName)
    {
        // strangely there will be no onUpdateProperties for properties removed via removeAspect
        final Map<QName, Serializable> properties = this.nodeService.getProperties(nodeRef);

        boolean moveIfChanged = false;
        if (this.moveStoresOnChangeOptionPropertyQName != null)
        {
            final Serializable moveStoresOnChangeOptionValue = properties.get(this.moveStoresOnChangeOptionPropertyQName);
            // explicit value wins
            if (moveStoresOnChangeOptionValue != null)
            {
                moveIfChanged = Boolean.TRUE.equals(moveStoresOnChangeOptionValue);
            }
            else
            {
                moveIfChanged = this.moveStoresOnChange;
            }
        }
        else
        {
            moveIfChanged = this.moveStoresOnChange;
        }

        if (moveIfChanged)
        {
            final Serializable selectorValue = properties.get(this.selectorPropertyQName);

            ContentStore oldStore = this.storeBySelectorPropertyValue.get(selectorValue);
            final ContentStore newStore = this.fallbackStore;
            if (oldStore == null)
            {
                oldStore = this.fallbackStore;
            }

            if (oldStore != newStore || (oldStore == newStore && newStore != this.fallbackStore))
            {
                final Map<QName, Serializable> updates = new HashMap<>();

                final ContentStore sourceStore = oldStore;
                ContentStoreContext.executeInNewContext(new ContentStoreOperation<Void>()
                {

                    /**
                     * {@inheritDoc}
                     */
                    @Override
                    public Void execute()
                    {
                        SelectorPropertyContentStore.this.processContentPropertiesMove(nodeRef, sourceStore, newStore, updates, properties);
                        return null;
                    }
                });

                if (!updates.isEmpty())
                {
                    this.nodeService.addProperties(nodeRef, updates);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onUpdateProperties(final NodeRef nodeRef, final Map<QName, Serializable> before, final Map<QName, Serializable> after)
    {
        boolean moveIfChanged = false;
        if (this.moveStoresOnChangeOptionPropertyQName != null)
        {
            final Serializable moveStoresOnChangeOptionValue = after.get(this.moveStoresOnChangeOptionPropertyQName);
            // explicit value wins
            if (moveStoresOnChangeOptionValue != null)
            {
                moveIfChanged = Boolean.TRUE.equals(moveStoresOnChangeOptionValue);
            }
            else
            {
                moveIfChanged = this.moveStoresOnChange;
            }
        }
        else
        {
            moveIfChanged = this.moveStoresOnChange;
        }

        if (moveIfChanged)
        {
            final Serializable selectorValueBefore = before.get(this.selectorPropertyQName);
            final Serializable selectorValueAfter = after.get(this.selectorPropertyQName);

            if (!EqualsHelper.nullSafeEquals(selectorValueBefore, selectorValueAfter))
            {
                ContentStore oldStore = this.storeBySelectorPropertyValue.get(selectorValueBefore);
                ContentStore newStore = this.storeBySelectorPropertyValue.get(selectorValueAfter);
                if (oldStore == null)
                {
                    oldStore = this.fallbackStore;
                }
                if (newStore == null)
                {
                    newStore = this.fallbackStore;
                }

                if (oldStore != newStore || (oldStore == newStore && newStore != this.fallbackStore))
                {
                    final Map<QName, Serializable> updates = new HashMap<>();
                    // get up-to-date properties (after may be out-of-date slightly due to policy cascading / nested calls)
                    final Map<QName, Serializable> properties = this.nodeService.getProperties(nodeRef);

                    final ContentStore sourceStore = oldStore;
                    final ContentStore targetStore = newStore;
                    ContentStoreContext.executeInNewContext(new ContentStoreOperation<Void>()
                    {

                        /**
                         * {@inheritDoc}
                         */
                        @Override
                        public Void execute()
                        {
                            SelectorPropertyContentStore.this.processContentPropertiesMove(nodeRef, sourceStore, targetStore, updates,
                                    properties);
                            return null;
                        }
                    });

                    if (!updates.isEmpty())
                    {
                        this.nodeService.addProperties(nodeRef, updates);
                    }
                }
            }
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected List<ContentStore> getAllStores()
    {
        return Collections.unmodifiableList(this.allStores);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected ContentStore selectWriteStore(final ContentContext ctx)
    {
        final ContentStore store;

        if (this.isRoutable(ctx))
        {
            final String contentUrl = ctx.getContentUrl();
            ContentStore valueStore = null;

            if (ctx instanceof NodeContentContext)
            {
                final NodeRef nodeRef = ((NodeContentContext) ctx).getNodeRef();
                final String value = DefaultTypeConverter.INSTANCE.convert(String.class,
                        this.nodeService.getProperty(nodeRef, this.selectorPropertyQName));

                LOGGER.debug("Looking up store for node {} and value {} of property {}", nodeRef, value, this.selectorPropertyQName);
                valueStore = this.storeBySelectorPropertyValue.get(value);
                if (valueStore != null)
                {
                    LOGGER.debug("Selecting store for value {} to write {}", value, ctx);
                }
                else
                {
                    LOGGER.debug("No store registered for value {}", value, ctx);
                }
            }

            if (valueStore == null && contentUrl != null)
            {
                LOGGER.debug("Selecting store based on provided content URL to write {}", ctx);
                valueStore = this.getStore(contentUrl, false);
            }

            if (valueStore == null)
            {
                LOGGER.debug("Selecting fallback store to write {}", ctx);
                valueStore = this.fallbackStore;
            }

            store = valueStore;
        }
        else
        {
            LOGGER.debug("Selecting fallback store to write {}", ctx);
            store = this.fallbackStore;
        }

        return store;
    }

    protected void processContentPropertiesMove(final NodeRef nodeRef, final ContentStore oldStore, final ContentStore newStore,
            final Map<QName, Serializable> updates, final Map<QName, Serializable> properties)
    {
        if (this.routeContentPropertyQNames != null && !this.routeContentPropertyQNames.isEmpty())
        {
            for (final QName propertyQName : this.routeContentPropertyQNames)
            {
                final Serializable value = properties.get(propertyQName);
                this.processContentPropertyMove(nodeRef, oldStore, newStore, propertyQName, value, updates);
            }
        }
        else
        {
            for (final Entry<QName, Serializable> entry : properties.entrySet())
            {
                final QName propertyQName = entry.getKey();
                final PropertyDefinition propertyDefinition = this.dictionaryService.getProperty(propertyQName);
                if (propertyDefinition != null && DataTypeDefinition.CONTENT.equals(propertyDefinition.getDataType().getName()))
                {
                    final Serializable value = entry.getValue();
                    this.processContentPropertyMove(nodeRef, oldStore, newStore, propertyQName, value, updates);
                }
            }
        }
    }

    protected void processContentPropertyMove(final NodeRef nodeRef, final ContentStore oldStore, final ContentStore newStore,
            final QName propertyQName, final Serializable value, final Map<QName, Serializable> updates)
    {
        if (value instanceof ContentData)
        {
            final ContentData updatedContentData = this.copyContent(oldStore, newStore, (ContentData) value, nodeRef, propertyQName);
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
                    final ContentData updatedContentData = this.copyContent(oldStore, newStore, (ContentData) valueElement, nodeRef,
                            propertyQName);
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

    protected ContentData copyContent(final ContentStore oldStore, final ContentStore newStore, final ContentData oldData,
            final NodeRef nodeRef, final QName propertyQName)
    {
        this.ensureInitializersAreSet();
        final NodeContentContext initializerContext = new NodeContentContext(null, oldData.getContentUrl(), nodeRef, propertyQName);
        for (final ContentStoreContextInitializer initializer : this.contentStoreContextInitializers)
        {
            initializer.initialize(initializerContext);
        }

        ContentData updatedContentData;

        final String oldContentUrl = oldData.getContentUrl();
        final Pair<String, String> urlParts = this.getContentUrlParts(oldContentUrl);
        final String protocol = urlParts.getFirst();
        final String oldWildcardContentUrl = StoreConstants.WILDCARD_PROTOCOL + oldContentUrl.substring(protocol.length());

        if (oldStore.isContentUrlSupported(oldWildcardContentUrl) && newStore.isContentUrlSupported(oldWildcardContentUrl)
                && newStore.exists(oldWildcardContentUrl))
        {
            final ContentReader reader = oldStore.getReader(oldWildcardContentUrl);
            if (!EqualsHelper.nullSafeEquals(oldContentUrl, reader.getContentUrl()))
            {
                reader.setMimetype(oldData.getMimetype());
                reader.setEncoding(oldData.getEncoding());
                reader.setLocale(oldData.getLocale());

                updatedContentData = reader.getContentData();
            }
            else
            {
                updatedContentData = null;
            }
        }
        else if (!newStore.isContentUrlSupported(oldContentUrl) || !newStore.exists(oldContentUrl))
        {
            final ContentReader reader = oldStore.getReader(oldContentUrl);
            if (reader == null || !reader.exists())
            {
                throw new AlfrescoRuntimeException("Can't copy content since original content does not exist");
            }

            final NodeContentContext contentContext = new NodeContentContext(reader,
                    newStore.isContentUrlSupported(oldWildcardContentUrl) ? oldWildcardContentUrl : oldContentUrl, nodeRef, propertyQName);
            final ContentWriter writer = newStore.getWriter(contentContext);

            final String newContentUrl = writer.getContentUrl();

            // ensure content cleanup on rollback (only if a new, unique URL was created
            if (!EqualsHelper.nullSafeEquals(oldContentUrl, newContentUrl))
            {
                final Set<String> urlsToDelete = TransactionalResourceHelper.getSet(KEY_POST_ROLLBACK_DELETION_URLS);
                urlsToDelete.add(newContentUrl);
            }

            writer.putContent(reader);

            // copy manually to keep original values (writing into different writer may change, e.g. size, due to transparent
            // transformations, i.e. compression)
            updatedContentData = new ContentData(writer.getContentUrl(), oldData.getMimetype(), oldData.getSize(), oldData.getEncoding(),
                    oldData.getLocale());
        }
        else
        {
            updatedContentData = null;
        }

        return updatedContentData;
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
        PropertyCheck.mandatory(this, "selectorClassName", this.selectorClassName);
        PropertyCheck.mandatory(this, "selectorPropertyName", this.selectorPropertyName);

        this.selectorClassQName = QName.resolveToQName(this.namespaceService, this.selectorClassName);
        this.selectorPropertyQName = QName.resolveToQName(this.namespaceService, this.selectorPropertyName);
        PropertyCheck.mandatory(this, "classQName", this.selectorClassQName);
        PropertyCheck.mandatory(this, "propertyQName", this.selectorPropertyQName);

        final ClassDefinition classDefinition = this.dictionaryService.getClass(this.selectorClassQName);
        if (classDefinition == null)
        {
            throw new IllegalStateException(this.selectorClassName + " is not a valid content model class");
        }

        final PropertyDefinition propertyDefinition = this.dictionaryService.getProperty(this.selectorPropertyQName);
        if (propertyDefinition == null || !DataTypeDefinition.TEXT.equals(propertyDefinition.getDataType().getName())
                || propertyDefinition.isMultiValued())
        {
            throw new IllegalStateException(
                    this.selectorPropertyName + " is not a valid content model property of type single-valued d:text");
        }
    }

    private void afterPropertiesSet_setupStoreData()
    {
        PropertyCheck.mandatory(this, "storeBySelectorPropertyValue", this.storeBySelectorPropertyValue);
        if (this.storeBySelectorPropertyValue.isEmpty())
        {
            throw new IllegalStateException("No stores have been defined for property values");
        }

        this.allStores = new ArrayList<>();
        for (final ContentStore store : this.storeBySelectorPropertyValue.values())
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
            this.policyComponent.bindClassBehaviour(OnUpdatePropertiesPolicy.QNAME, this.selectorClassQName,
                    new JavaBehaviour(this, "onUpdateProperties", NotificationFrequency.EVERY_EVENT));

            final ClassDefinition classDefinition = this.dictionaryService.getClass(this.selectorClassQName);
            if (classDefinition.isAspect())
            {
                this.policyComponent.bindClassBehaviour(BeforeRemoveAspectPolicy.QNAME, this.selectorClassQName,
                        new JavaBehaviour(this, "beforeRemoveAspect", NotificationFrequency.EVERY_EVENT));
                this.policyComponent.bindClassBehaviour(OnAddAspectPolicy.QNAME, this.selectorClassQName,
                        new JavaBehaviour(this, "onAddAspect", NotificationFrequency.EVERY_EVENT));
            }
        }
    }

    private void afterPropertiesSet_setupConstraint()
    {
        if (this.selectorValuesConstraintShortName != null && !this.selectorValuesConstraintShortName.trim().isEmpty())
        {
            final ListOfValuesConstraint lovConstraint = new ListOfValuesConstraint();
            lovConstraint.setShortName(this.selectorValuesConstraintShortName);
            lovConstraint.setRegistry(this.constraintRegistry);
            lovConstraint.setAllowedValues(new ArrayList<>(this.storeBySelectorPropertyValue.keySet()));
            lovConstraint.initialize();
        }
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
}
