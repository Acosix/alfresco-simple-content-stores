/*
 * Copyright 2017, 2018 Acosix GmbH
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
package de.acosix.alfresco.simplecontentstores.repo.beans;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.alfresco.error.AlfrescoRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.ChildBeanDefinition;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.ManagedMap;

/**
 * @author Axel Faust
 */
public class SimpleContentStoresBeanDefinitionEmitter implements BeanDefinitionRegistryPostProcessor, BeanNameAware
{

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleContentStoresBeanDefinitionEmitter.class);

    private static final String PROP_ROOT_STORE = "simpleContentStores.rootStore";

    private static final String PROP_CUSTOM_STORES = "simpleContentStores.customStores";

    private static final String PROP_CUSTOM_STORE_PREFIX = "simpleContentStores.customStore";

    private static final String STORE_TEMPLATE_PREFIX = "simpleContentStoresTemplate-";

    protected String beanName;

    protected List<BeanDefinitionRegistryPostProcessor> dependsOn;

    protected boolean executed;

    protected Boolean enabled;

    protected String enabledPropertyKey;

    protected List<String> enabledPropertyKeys;

    protected Properties propertiesSource;

    protected String rootStoreProxyName;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBeanName(final String name)
    {
        this.beanName = name;
    }

    /**
     * @param dependsOn
     *            the dependsOn to set
     */
    public void setDependsOn(final List<BeanDefinitionRegistryPostProcessor> dependsOn)
    {
        this.dependsOn = dependsOn;
    }

    /**
     * @param enabled
     *            the enabled to set
     */
    public void setEnabled(final boolean enabled)
    {
        this.enabled = enabled;
    }

    /**
     * @param enabledPropertyKey
     *            the enabledPropertyKey to set
     */
    public void setEnabledPropertyKey(final String enabledPropertyKey)
    {
        this.enabledPropertyKey = enabledPropertyKey;
    }

    /**
     * @param enabledPropertyKeys
     *            the enabledPropertyKeys to set
     */
    public void setEnabledPropertyKeys(final List<String> enabledPropertyKeys)
    {
        this.enabledPropertyKeys = enabledPropertyKeys;
    }

    /**
     * @param propertiesSource
     *            the propertiesSource to set
     */
    public void setPropertiesSource(final Properties propertiesSource)
    {
        this.propertiesSource = propertiesSource;
    }

    /**
     * @param rootStoreProxyName
     *            the rootStoreProxyName to set
     */
    public void setRootStoreProxyName(final String rootStoreProxyName)
    {
        this.rootStoreProxyName = rootStoreProxyName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void postProcessBeanFactory(final ConfigurableListableBeanFactory beanFactory) throws BeansException
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void postProcessBeanDefinitionRegistry(final BeanDefinitionRegistry registry) throws BeansException
    {
        if (!this.executed)
        {
            final boolean enabled = this.isEnabled();

            if (enabled)
            {
                if (this.dependsOn != null)
                {
                    for (final BeanDefinitionRegistryPostProcessor x : this.dependsOn)
                    {
                        x.postProcessBeanDefinitionRegistry(registry);
                }
                }

                LOGGER.info("[{}] patch is being applied", this.beanName);
                this.emitCustomStoreBeanDefinitions(registry);
                this.processRootStore(registry);
            }
            else
            {
                LOGGER.info("[{}] patch will not be applied as it has been marked as inactive", this.beanName);
            }
            this.executed = true;
        }
    }

    protected boolean isEnabled()
    {
        Boolean enabled = this.enabled;
        if (!Boolean.FALSE.equals(enabled) && this.enabledPropertyKey != null && !this.enabledPropertyKey.isEmpty())
        {
            final String property = this.propertiesSource.getProperty(this.enabledPropertyKey);
            enabled = (property != null ? Boolean.valueOf(property) : Boolean.FALSE);
        }

        if (!Boolean.FALSE.equals(enabled) && this.enabledPropertyKeys != null && !this.enabledPropertyKeys.isEmpty())
        {
            final AtomicBoolean enabled2 = new AtomicBoolean(true);
            for (final String key : this.enabledPropertyKeys)
            {
                final String property = this.propertiesSource.getProperty(key);
                enabled2.compareAndSet(true, property != null ? Boolean.parseBoolean(property) : false);
            }
            enabled = Boolean.valueOf(enabled2.get());
        }

        return Boolean.TRUE.equals(enabled);
    }

    protected void processRootStore(final BeanDefinitionRegistry registry)
    {
        final String realRootStore = this.propertiesSource.getProperty(PROP_ROOT_STORE, "fileContentStore");
        LOGGER.info("Setting {} as root content store", realRootStore);

        // complete the proxy definition
        final BeanDefinition rootStoreProxyDefinition = registry.getBeanDefinition(this.rootStoreProxyName);
        rootStoreProxyDefinition.getPropertyValues().add("target", new RuntimeBeanReference(realRootStore));
        rootStoreProxyDefinition.getPropertyValues().add("singleton", Boolean.TRUE);

        final BeanDefinition contentServiceDefinition = registry.getBeanDefinition("contentService");
        contentServiceDefinition.getPropertyValues().add("store", new RuntimeBeanReference(this.rootStoreProxyName));

        // baseMultiTAdminService may not be present if mt-*-context.xml files aren't included
        final BeanDefinition baseMultiTAdminServiceDefinition = registry.getBeanDefinition("baseMultiTAdminService");
        if (baseMultiTAdminServiceDefinition != null)
        {
            // though the property is named tenantFileContentStore it actually requires only ContentStore interface and runtime checks are
            // against ContentStoreCaps or TenantDeployer (TenantRoutingContentStore was used sometime pre-5.x)
            baseMultiTAdminServiceDefinition.getPropertyValues().add("tenantFileContentStore",
                    new RuntimeBeanReference(this.rootStoreProxyName));
        }
    }

    protected void emitCustomStoreBeanDefinitions(final BeanDefinitionRegistry registry)
    {
        final String customStoreNames = this.propertiesSource.getProperty(PROP_CUSTOM_STORES, "");
        if (!customStoreNames.trim().isEmpty())
        {
            LOGGER.info("Defined custom store names: {}", customStoreNames);
            final String[] storeNames = customStoreNames.split(",");
            for (String storeName : storeNames)
            {
                storeName = storeName.trim();
                if (storeName.isEmpty())
                {
                    LOGGER.warn("Potential typo / misconfiguration in custom store names - a store name ended up empty");
                }
                else
                {
                    this.emitCustomStoreBeanDefinition(registry, storeName);
                }
            }
        }
        else
        {
            LOGGER.info("No custom stores have been defined");
        }
    }

    protected void emitCustomStoreBeanDefinition(final BeanDefinitionRegistry registry, final String storeName)
    {
        if (registry.containsBeanDefinition(storeName))
        {
            throw new AlfrescoRuntimeException(
                    storeName + " (custom content store) cannot be defined - a bean with same name already exists");
        }

        final MessageFormat mf = new MessageFormat("{0}.{1}.", Locale.ENGLISH);
        final String prefix = mf.format(new Object[] { PROP_CUSTOM_STORE_PREFIX, storeName });
        final String typeProperty = prefix + "type";
        final String typeValue = this.propertiesSource.getProperty(typeProperty);

        if (typeValue != null && !typeValue.isEmpty())
        {
            LOGGER.debug("Emitting bean definition for custom store {} based on template {}", storeName, typeValue);
            final BeanDefinition storeBeanDefinition = new ChildBeanDefinition(STORE_TEMPLATE_PREFIX + typeValue);
            storeBeanDefinition.setScope(BeanDefinition.SCOPE_SINGLETON);

            final Set<String> propertyNames = this.propertiesSource.stringPropertyNames();
            for (final String propertyName : propertyNames)
            {
                if (propertyName.startsWith(prefix) && !typeProperty.equals(propertyName))
                {
                    this.handleBeanProperty(storeBeanDefinition, propertyName, this.propertiesSource.getProperty(propertyName));
                }
            }

            registry.registerBeanDefinition(storeName, storeBeanDefinition);
        }
        else
        {
            LOGGER.warn("Custom store {} does not define a type", storeName);
            throw new AlfrescoRuntimeException(storeName + " (custom content store) has not been given a type");
        }
    }

    @SuppressWarnings("unchecked")
    protected void handleBeanProperty(final BeanDefinition definition, final String propertyKey, final String propertyValue)
    {
        final String[] nameFragments = propertyKey.split("\\.");

        final boolean isReference = nameFragments.length > 2 && "ref".equals(nameFragments[nameFragments.length - 2]);
        final boolean isValue = nameFragments.length > 2 && "value".equals(nameFragments[nameFragments.length - 2]);

        final boolean isMap = nameFragments.length > 4 && "map".equals(nameFragments[nameFragments.length - 4]);
        final boolean isList = nameFragments.length > 3 && "list".equals(nameFragments[nameFragments.length - 3]);

        final String propertyName = nameFragments[nameFragments.length - (isMap ? 3 : 1)];
        final MutablePropertyValues beanPropertyValues = definition.getPropertyValues();
        final PropertyValue configuredValue = beanPropertyValues.getPropertyValue(propertyName);

        if (isMap)
        {
            ManagedMap<Object, Object> map;

            if (configuredValue == null)
            {
                map = new ManagedMap<>();
                beanPropertyValues.add(propertyName, map);
            }
            else
            {
                final Object value = configuredValue.getValue();
                if (value instanceof ManagedMap<?, ?>)
                {
                    map = (ManagedMap<Object, Object>) value;
                }
                else
                {
                    LOGGER.warn("Inconsistent configured value type in bean property {}", propertyName);
                    throw new AlfrescoRuntimeException("Inconsistency in custom store property values");
                }
            }

            final String keyName = nameFragments[nameFragments.length - 1];
            if (isReference)
            {
                LOGGER.trace("Adding reference to bean {} as value of map entry with key {} of property {}", propertyValue, keyName,
                        propertyName);
                map.put(keyName, new RuntimeBeanReference(propertyValue));
            }
            else if (isValue)
            {
                LOGGER.trace("Adding {} as value of map entry with key {} of property {}", propertyValue, keyName, propertyName);
                map.put(keyName, propertyValue);
            }
            else
            {
                LOGGER.warn("Custom store config property {} is of an unsupported format", propertyKey);
            }
        }
        else if (isList)
        {
            if (configuredValue != null)
            {
                LOGGER.warn("Bean property {} already set", propertyName);
                throw new AlfrescoRuntimeException("Custom store property value already set");
            }

            if (!isReference && !isValue)
            {
                LOGGER.warn("Custom store config property {} is of an unsupported format", propertyKey);
                throw new AlfrescoRuntimeException("Custom store config property is of an unsupported format");
            }

            final ManagedList<Object> list = new ManagedList<>();
            final String[] values = propertyValue.split(",");
            for (final String value : values)
            {
                if (isReference)
                {
                    LOGGER.trace("Adding reference to bean {} as list entry of property {}", propertyValue, propertyName);
                    list.add(new RuntimeBeanReference(value));
                }
                else
                {
                    LOGGER.trace("Adding value {} as list entry of property {}", propertyValue, propertyName);
                    list.add(value);
                }
            }
            beanPropertyValues.add(propertyName, list);
        }
        else if (isReference)
        {
            if (configuredValue != null)
            {
                LOGGER.warn("Bean property {} already set", propertyName);
                throw new AlfrescoRuntimeException("Custom store property value already set");
            }

            LOGGER.trace("Setting reference to bean {} as value of property {}", propertyValue, propertyName);
            beanPropertyValues.add(propertyName, new RuntimeBeanReference(propertyValue));
        }
        else if (isValue)
        {
            if (configuredValue != null)
            {
                LOGGER.warn("Bean property {} already set", propertyName);
                throw new AlfrescoRuntimeException("Custom store property value already set");
            }

            LOGGER.trace("Setting {} as value of property {}", propertyValue, propertyName);
            beanPropertyValues.add(propertyName, propertyValue);
        }
        else
        {
            LOGGER.warn("Custom store config property {} is of an unsupported format", propertyKey);
        }
    }
}
