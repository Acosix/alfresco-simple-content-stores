/*
 * Copyright 2017 - 2024 Acosix GmbH
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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.alfresco.util.PropertyCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.PlaceholderConfigurerSupport;
import org.springframework.util.PropertyPlaceholderHelper;

/**
 * Instances of this class perform a translation of a specific global properties entry to replace it with the version-specific Hazelcast
 * class name. Since the global properties are processed very early in the Spring lifecycle, this class cannot use {@link BeanPostProcessor}
 * to perform the translation during instantiation of the {@link Properties} instance, and relies on cache factories to have a
 * {@code depends-on} declaration to ensure its translation is run before a cache is instantiated.
 *
 * @author Axel Faust
 */
public class MergePolicyTranslator implements InitializingBean
{

    private static final Logger LOGGER = LoggerFactory.getLogger(MergePolicyTranslator.class);

    private static final String PROPERTY_NAME_PREFIX = "cache.acosix-simple-content-stores-";

    private static final String PROPERTY_NAME_SUFFIX = ".merge-policy";

    private static final Map<String, List<String>> VALUE_CLASS_TRANSLATIONS;
    static
    {
        final Map<String, List<String>> translations = new HashMap<>();

        translations.put("hz.NO_MERGE", Collections.unmodifiableList(
                Arrays.asList("com.hazelcast.spi.merge.PassThroughMergePolicy", "com.hazelcast.map.merge.PassThroughMergePolicy")));
        translations.put("hz.ADD_NEW_ENTRY", Collections.unmodifiableList(
                Arrays.asList("com.hazelcast.spi.merge.PutIfAbsentMergePolicy", "com.hazelcast.map.merge.PutIfAbsentMapMergePolicy")));
        translations.put("hz.HIGHER_HITS", Collections.unmodifiableList(
                Arrays.asList("com.hazelcast.spi.merge.HigherHitsMergePolicy", "com.hazelcast.map.merge.HigherHitsMapMergePolicy")));
        translations.put("hz.LATEST_UPDATE", Collections.unmodifiableList(
                Arrays.asList("com.hazelcast.spi.merge.LatestAccessMergePolicy", "com.hazelcast.map.merge.LatestUpdateMapMergePolicy")));

        VALUE_CLASS_TRANSLATIONS = Collections.unmodifiableMap(translations);
    }

    protected String placeholderPrefix = PlaceholderConfigurerSupport.DEFAULT_PLACEHOLDER_PREFIX;

    protected String placeholderSuffix = PlaceholderConfigurerSupport.DEFAULT_PLACEHOLDER_SUFFIX;

    protected String valueSeparator = PlaceholderConfigurerSupport.DEFAULT_VALUE_SEPARATOR;

    protected PropertyPlaceholderHelper placeholderHelper;

    protected Properties globalProperties;

    /**
     * {@inheritDoc}
     */
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "globalProperties", this.globalProperties);
        this.placeholderHelper = new PropertyPlaceholderHelper(this.placeholderPrefix, this.placeholderSuffix, this.valueSeparator, true);

        final Map<String, String> resolvedTranslation = new HashMap<>();
        for (final String propertyName : globalProperties.stringPropertyNames())
        {
            if (propertyName.startsWith(PROPERTY_NAME_PREFIX) && propertyName.endsWith(PROPERTY_NAME_SUFFIX))
            {
                String property = globalProperties.getProperty(propertyName);
                property = this.placeholderHelper.replacePlaceholders(property, globalProperties);
                String translation = resolvedTranslation.computeIfAbsent(property, configured -> {
                    final List<String> classNames = VALUE_CLASS_TRANSLATIONS.get(configured);
                    if (classNames != null)
                    {
                        LOGGER.debug("Trying to translate merge-policy value {}", configured);
                        for (final String className : classNames)
                        {
                            try
                            {
                                Class.forName(className);

                                LOGGER.debug("Translating Hazelcast merge-policy value {} to class {}", configured, className);
                                return className;
                            }
                            catch (ClassNotFoundException | NoClassDefFoundError ignore)
                            {
                                LOGGER.debug("Class {} is not available as translation for {}", className, configured);
                            }
                        }
                    }

                    return null;
                });

                if (translation != null)
                {
                    globalProperties.put(propertyName, translation);
                }
            }
        }
    }

    /**
     * @param placeholderPrefix
     *     the placeholderPrefix to set
     */
    public void setPlaceholderPrefix(final String placeholderPrefix)
    {
        this.placeholderPrefix = placeholderPrefix;
    }

    /**
     * @param placeholderSuffix
     *     the placeholderSuffix to set
     */
    public void setPlaceholderSuffix(final String placeholderSuffix)
    {
        this.placeholderSuffix = placeholderSuffix;
    }

    /**
     * @param valueSeparator
     *     the valueSeparator to set
     */
    public void setValueSeparator(final String valueSeparator)
    {
        this.valueSeparator = valueSeparator;
    }

    /**
     * @param globalProperties
     *     the globalProperties to set
     */
    public void setGlobalProperties(Properties globalProperties)
    {
        this.globalProperties = globalProperties;
    }

}
