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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Map;

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
import org.alfresco.service.cmr.dictionary.ClassDefinition;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.dictionary.PropertyDefinition;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.datatype.DefaultTypeConverter;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.EqualsHelper;
import org.alfresco.util.PropertyCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Axel Faust
 */
public class SelectorPropertyContentStore extends PropertyRestrictableRoutingContentStore<Serializable>
        implements OnUpdatePropertiesPolicy, OnAddAspectPolicy, BeforeRemoveAspectPolicy
{

    private static final Logger LOGGER = LoggerFactory.getLogger(SelectorPropertyContentStore.class);

    protected ConstraintRegistry constraintRegistry;

    protected String selectorClassName;

    protected transient QName selectorClassQName;

    protected String selectorPropertyName;

    protected transient QName selectorPropertyQName;

    protected Map<String, ContentStore> storeBySelectorPropertyValue;

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

        PropertyCheck.mandatory(this, "constraintRegistry", this.constraintRegistry);

        this.afterPropertiesSet_validateSelectors();
        this.afterPropertiesSet_setupStoreData();
        this.afterPropertiesSet_setupChangePolicies();
        this.afterPropertiesSet_setupConstraint();
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
        // aspect may be added as part of node creation, which is not really an update, but we can't properly eliminate this scenario
        // without significant overhead

        // typically, no content data to move would exist in this case, except for the special scenario of a copy, in which case the content
        // would only be moved if the selector value from the original is not copied as-is or modified as part of the copy behaviour
        // callback logic, potentially resulting in a different store to be selected for the content that was copied over as well

        // there will be no onUpdateProperties for properties added via addAspect or lazy aspect application as result of
        // addProperties/setProperties
        LOGGER.debug("Processing onAddAspect for {}", nodeRef);

        final Map<QName, Serializable> properties = this.nodeService.getProperties(nodeRef);
        final Serializable selectorValueAfter = properties.get(this.selectorPropertyQName);

        if (selectorValueAfter != null)
        {
            LOGGER.debug("Selector property value changed from null to {} for {}", selectorValueAfter, nodeRef);
            this.checkAndProcessContentPropertiesMove(nodeRef, this.moveStoresOnChange, this.moveStoresOnChangeOptionPropertyQName,
                    selectorValueAfter);
        }
        else
        {
            LOGGER.debug("No change in selector property value found for {}", nodeRef);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeRemoveAspect(final NodeRef nodeRef, final QName aspectQName)
    {
        // there will be no onUpdateProperties for properties removed via removeAspect
        LOGGER.debug("Processing onRemoveAspect for {}", nodeRef);

        final Map<QName, Serializable> properties = this.nodeService.getProperties(nodeRef);
        final Serializable selectorValueBefore = properties.get(this.selectorPropertyQName);

        if (selectorValueBefore != null)
        {
            LOGGER.debug("Selector property value will change from {} to null for {}", selectorValueBefore, nodeRef);
            this.checkAndProcessContentPropertiesMove(nodeRef, this.moveStoresOnChange, this.moveStoresOnChangeOptionPropertyQName, null);
        }
        else
        {
            LOGGER.debug("No change in selector property value found for {}", nodeRef);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onUpdateProperties(final NodeRef nodeRef, final Map<QName, Serializable> before, final Map<QName, Serializable> after)
    {
        // don't handle creations - here we can actually properly and efficiently eliminate the scenario (other than during onAddAspect)
        if (!before.isEmpty())
        {
            LOGGER.debug("Processing onUpdateProperties for {}", nodeRef);

            final Serializable selectorValueBefore = before.get(this.selectorPropertyQName);
            final Serializable selectorValueAfter = after.get(this.selectorPropertyQName);

            if (!EqualsHelper.nullSafeEquals(selectorValueBefore, selectorValueAfter))
            {
                LOGGER.debug("Selector property value changed from {} to {} for {}", selectorValueBefore, selectorValueAfter, nodeRef);
                this.checkAndProcessContentPropertiesMove(nodeRef, this.moveStoresOnChange, this.moveStoresOnChangeOptionPropertyQName,
                        selectorValueAfter);
            }
            else
            {
                LOGGER.debug("No change in selector property value found for {}", nodeRef);
            }
        }
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
            final String value = DefaultTypeConverter.INSTANCE.convert(String.class,
                    this.nodeService.getProperty(nodeRef, this.selectorPropertyQName));

            LOGGER.debug("Looking up store for node {} and value {} of property {}", nodeRef, value, this.selectorPropertyQName);
            if (this.storeBySelectorPropertyValue.containsKey(value))
            {
                LOGGER.debug("Selecting store for value {} to write {}", value, ctx);
                writeStore = this.storeBySelectorPropertyValue.get(value);
            }
            else
            {
                LOGGER.debug("No store registered for value {} - delegating to super.selectWiteStoreFromRoute", value);
                writeStore = super.selectWriteStoreFromRoutes(ctx);
            }
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
    protected ContentStore selectStoreForContentDataMove(final NodeRef nodeRef, final QName propertyQName, final ContentData contentData,
            final Serializable selectorValue)
    {
        ContentStore targetStore;
        if (this.storeBySelectorPropertyValue.containsKey(selectorValue))
        {
            LOGGER.debug("Selecting store for value {} to move {}", selectorValue, contentData);
            targetStore = this.storeBySelectorPropertyValue.get(selectorValue);
        }
        else
        {
            LOGGER.debug("No store registered for value {} - delegating to super.selectStoreForContentDataMove to move {}", selectorValue,
                    contentData);
            targetStore = super.selectStoreForContentDataMove(nodeRef, propertyQName, contentData, selectorValue);
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
        PropertyCheck.mandatory(this, "selectorClassName", this.selectorClassName);
        PropertyCheck.mandatory(this, "selectorPropertyName", this.selectorPropertyName);

        this.selectorClassQName = QName.resolveToQName(this.namespaceService, this.selectorClassName);
        this.selectorPropertyQName = QName.resolveToQName(this.namespaceService, this.selectorPropertyName);
        PropertyCheck.mandatory(this, "selectorClassQName", this.selectorClassQName);
        PropertyCheck.mandatory(this, "selectorPropertyQName", this.selectorPropertyQName);

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
}
