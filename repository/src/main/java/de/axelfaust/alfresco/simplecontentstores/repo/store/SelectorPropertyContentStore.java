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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.alfresco.repo.content.ContentContext;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.content.NodeContentContext;
import org.alfresco.repo.dictionary.constraint.ConstraintRegistry;
import org.alfresco.repo.dictionary.constraint.ListOfValuesConstraint;
import org.alfresco.service.cmr.dictionary.ClassDefinition;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.dictionary.PropertyDefinition;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.datatype.DefaultTypeConverter;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.PropertyCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Axel Faust
 */
public class SelectorPropertyContentStore extends CommonRoutingContentStore
{

    // TODO Introduce abstract base class for "move-capable" content stores that provide policies to bind to

    private static final Logger LOGGER = LoggerFactory.getLogger(SelectorPropertyContentStore.class);

    protected NodeService nodeService;

    protected String selectorClassName;

    protected transient QName selectorClassQName;

    protected String selectorPropertyName;

    protected transient QName selectorPropertyQName;

    protected Map<String, ContentStore> storeBySelectorPropertyValue;

    protected transient Map<String, ContentStore> storeByPathPrefix;

    protected transient String basePathPrefix;

    protected transient String fallbackStorePathPrefix;

    protected transient String alternativeFallbackStorePathPrefix;

    protected transient List<ContentStore> allStores;

    protected String selectorValuesConstraintShortName;

    protected ConstraintRegistry constraintRegistry;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        super.afterPropertiesSet();

        PropertyCheck.mandatory(this, "nodeService", this.nodeService);
        PropertyCheck.mandatory(this, "constraintRegistry", this.constraintRegistry);

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
            throw new IllegalStateException(this.selectorPropertyName
                    + " is not a valid content model property of type single-valued d:text");
        }

        PropertyCheck.mandatory(this, "storeBySelectorPropertyValue", this.storeBySelectorPropertyValue);
        if (this.storeBySelectorPropertyValue.isEmpty())
        {
            throw new IllegalStateException("No stores have been defined for property values");
        }

        // TODO Setup policy for handling changes

        this.allStores = new ArrayList<ContentStore>();
        this.storeByPathPrefix = new HashMap<String, ContentStore>();
        this.basePathPrefix = this.selectorPropertyQName.toPrefixString(this.namespaceService).replace(':', '_') + '@';
        for (final Entry<String, ContentStore> entry : this.storeBySelectorPropertyValue.entrySet())
        {
            final ContentStore store = entry.getValue();

            final String pathPrefix = this.basePathPrefix + entry.getKey();
            final PathPrefixingContentStoreFacade facade = new PathPrefixingContentStoreFacade(store, pathPrefix);
            this.storeByPathPrefix.put(pathPrefix, facade);

            if (!this.allStores.contains(store))
            {
                this.allStores.add(store);
            }
        }

        if (!this.allStores.contains(this.fallbackStore))
        {
            this.allStores.add(this.fallbackStore);
        }

        this.fallbackStorePathPrefix = this.basePathPrefix + "_default_";
        if (this.storeByPathPrefix.containsKey(this.fallbackStorePathPrefix))
        {
            this.alternativeFallbackStorePathPrefix = this.fallbackStorePathPrefix;
            this.fallbackStorePathPrefix = this.basePathPrefix + "_fallback_";
            if (this.storeByPathPrefix.containsKey(this.fallbackStorePathPrefix))
            {
                throw new IllegalStateException(
                        "Both _default_ and _fallback_ are used as selector property values - unable to handle fallback store fragment in content URLs");
            }

            this.storeByPathPrefix.put(this.fallbackStorePathPrefix, new PathPrefixingContentStoreFacade(this.fallbackStore,
                    this.fallbackStorePathPrefix));
        }
        else
        {
            this.storeByPathPrefix.put(this.fallbackStorePathPrefix, new PathPrefixingContentStoreFacade(this.fallbackStore,
                    this.fallbackStorePathPrefix));
            this.alternativeFallbackStorePathPrefix = this.basePathPrefix + "_fallback_";
        }

        if (this.selectorValuesConstraintShortName != null && !this.selectorValuesConstraintShortName.trim().isEmpty())
        {
            final ListOfValuesConstraint lovConstraint = new ListOfValuesConstraint();
            lovConstraint.setShortName(this.selectorValuesConstraintShortName);
            lovConstraint.setRegistry(this.constraintRegistry);
            lovConstraint.setAllowedValues(new ArrayList<String>(this.storeBySelectorPropertyValue.keySet()));
            lovConstraint.initialize();
        }
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
     * @param constraintRegistry
     *            the constraintRegistry to set
     */
    public void setConstraintRegistry(final ConstraintRegistry constraintRegistry)
    {
        this.constraintRegistry = constraintRegistry;
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

    protected List<ContentStore> getStores(final String contentUrl)
    {
        final List<ContentStore> contentStores = new ArrayList<ContentStore>();
        final int indexOfProtocolSeparator = contentUrl.indexOf("://");
        final String firstPathSegment = contentUrl.substring(indexOfProtocolSeparator + 3,
                contentUrl.indexOf('/', indexOfProtocolSeparator + 3));

        final ContentStore storeForPathSegment = this.storeByPathPrefix.get(firstPathSegment);
        if (storeForPathSegment != null)
        {
            contentStores.add(storeForPathSegment);
        }

        if (firstPathSegment.startsWith(this.basePathPrefix))
        {
            contentStores.add(this.storeByPathPrefix.get(this.fallbackStorePathPrefix));
            contentStores.add(new PathPrefixingContentStoreFacade(this.fallbackStore, this.alternativeFallbackStorePathPrefix));
        }

        return contentStores;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected ContentStore selectWriteStore(final ContentContext ctx)
    {
        final ContentStore store;

        final String contentUrl = ctx.getContentUrl();
        if (ctx instanceof NodeContentContext)
        {
            final NodeRef nodeRef = ((NodeContentContext) ctx).getNodeRef();
            final QName contentPropertyQName = ((NodeContentContext) ctx).getPropertyQName();

            if (nodeRef != null
                    && (this.routeContentPropertyQNames == null || this.routeContentPropertyQNames.contains(contentPropertyQName)))
            {
                final String value = DefaultTypeConverter.INSTANCE.convert(String.class,
                        this.nodeService.getProperty(nodeRef, this.selectorPropertyQName));

                LOGGER.debug("Looking up store for node {} and value {} of property {}", nodeRef, value, this.selectorPropertyQName);
                final ContentStore valueStore = this.storeBySelectorPropertyValue.get(value);
                if (valueStore != null)
                {
                    LOGGER.debug("Selecting store for value {} to write {}", value, ctx);
                    final String pathPrefix = MessageFormat.format("{0}{1}", this.basePathPrefix, value);
                    store = new PathPrefixingContentStoreFacade(valueStore, pathPrefix);
                }
                else if (contentUrl != null)
                {
                    LOGGER.debug("Selecting store based on provided content URL to write {}", ctx);
                    store = this.getStore(contentUrl, false);
                }
                else
                {
                    LOGGER.debug("No store registered for value {} - selecting fallback store to write {}", value, ctx);
                    store = new PathPrefixingContentStoreFacade(this.fallbackStore, this.fallbackStorePathPrefix);
                }
            }
            else if (contentUrl != null)
            {
                LOGGER.debug("Selecting store based on provided content URL to write {}", ctx);
                store = this.getStore(contentUrl, false);
            }
            else
            {
                LOGGER.debug("Selecting fallback store to write {}", ctx);
                store = new PathPrefixingContentStoreFacade(this.fallbackStore, this.fallbackStorePathPrefix);
            }
        }
        else if (contentUrl != null)
        {
            LOGGER.debug("Selecting store based on provided content URL to write {}", ctx);
            store = this.getStore(contentUrl, false);
        }
        else
        {
            LOGGER.debug("Selecting fallback store to write {}", ctx);
            store = new PathPrefixingContentStoreFacade(this.fallbackStore, this.fallbackStorePathPrefix);
        }

        return store;
    }
}
