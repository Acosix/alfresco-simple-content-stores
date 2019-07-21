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
package de.acosix.alfresco.simplecontentstores.repo.beans;

import java.io.File;
import java.util.Collections;

import org.alfresco.repo.cache.CacheFactory;
import org.alfresco.repo.cache.SimpleCache;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.content.caching.CachingContentStore;
import org.alfresco.repo.content.caching.ContentCacheImpl;
import org.alfresco.repo.content.caching.Key;
import org.alfresco.repo.content.caching.cleanup.CachedContentCleaner;
import org.alfresco.repo.content.caching.cleanup.CachedContentCleanupJob;
import org.alfresco.repo.content.caching.quota.QuotaManagerStrategy;
import org.alfresco.repo.content.caching.quota.UnlimitedQuotaStrategy;
import org.alfresco.util.AbstractTriggerBean;
import org.alfresco.util.CronTriggerBean;
import org.alfresco.util.PropertyCheck;
import org.alfresco.util.TriggerBean;
import org.quartz.Scheduler;
import org.quartz.SimpleTrigger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.support.DefaultSingletonBeanRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.scheduling.quartz.JobDetailBean;

import de.acosix.alfresco.simplecontentstores.repo.store.DisposableStandardQuotaStrategy;

/**
 * @author Axel Faust
 */
public class CachingContentStoreFactoryBean implements FactoryBean<CachingContentStore>, ApplicationContextAware, BeanFactoryAware,
        ApplicationEventPublisherAware, BeanNameAware, InitializingBean
{

    protected ApplicationContext applicationContext;

    protected ApplicationEventPublisher applicationEventPublisher;

    protected BeanFactory beanFactory;

    protected String beanName;

    protected CacheFactory<Key, String> cacheFactory;

    protected String cacheName;

    protected String cacheRoot;

    protected ContentStore backingStore;

    protected boolean cacheOnInbound;

    protected int maxCacheTries = 2;

    protected QuotaManagerStrategy quotaStrategy;

    protected boolean useStandardQuotaStrategy;

    protected int standardQuotaPanicThresholdPercent = 90;

    protected int standardQuotaCleanThresholdPercent = 80;

    protected int standardQuotaTargetUsagePercent = 70;

    protected int standardQuotaMaxUsageBytes = 0;

    protected int standardQuotaMaxFileSizeMebiBytes = 0;

    protected int standardQuotaNormalCleanThresholdSeconds = 0;

    protected long cleanerMinFileAgeMillis = 0;

    protected Integer cleanerMaxDeleteWatchCount;

    protected String cleanerCronExpression;

    protected long cleanerStartDelay = 0;

    protected long cleanerRepeatInterval = 5 * 60 * 1000l;

    protected int cleanerRepeatCount = SimpleTrigger.REPEAT_INDEFINITELY;

    protected Scheduler scheduler;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "cacheFactory", this.cacheFactory);
        // TODO Do we want to provide a default name convention?
        PropertyCheck.mandatory(this, "cacheName", this.cacheName);
        PropertyCheck.mandatory(this, "cacheRoot", this.cacheRoot);
        PropertyCheck.mandatory(this, "backingStore", this.backingStore);
        PropertyCheck.mandatory(this, "scheduler", this.scheduler);
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
     * {@inheritDoc}
     */
    @Override
    public void setApplicationEventPublisher(final ApplicationEventPublisher applicationEventPublisher)
    {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBeanFactory(final BeanFactory beanFactory) throws BeansException
    {
        this.beanFactory = beanFactory;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBeanName(final String name)
    {
        this.beanName = name;
    }

    /**
     * @param cacheFactory
     *            the cacheFactory to set
     */
    public void setCacheFactory(final CacheFactory<Key, String> cacheFactory)
    {
        this.cacheFactory = cacheFactory;
    }

    /**
     * @param cacheName
     *            the cacheName to set
     */
    public void setCacheName(final String cacheName)
    {
        this.cacheName = cacheName;
    }

    /**
     * @param cacheRoot
     *            the cacheRoot to set
     */
    public void setCacheRoot(final String cacheRoot)
    {
        this.cacheRoot = cacheRoot;
    }

    /**
     * @param backingStore
     *            the backingStore to set
     */
    public void setBackingStore(final ContentStore backingStore)
    {
        this.backingStore = backingStore;
    }

    /**
     * @param cacheOnInbound
     *            the cacheOnInbound to set
     */
    public void setCacheOnInbound(final boolean cacheOnInbound)
    {
        this.cacheOnInbound = cacheOnInbound;
    }

    /**
     * @param maxCacheTries
     *            the maxCacheTries to set
     */
    public void setMaxCacheTries(final int maxCacheTries)
    {
        this.maxCacheTries = maxCacheTries;
    }

    /**
     * @param quotaStrategy
     *            the quotaStrategy to set
     */
    public void setQuotaStrategy(final QuotaManagerStrategy quotaStrategy)
    {
        this.quotaStrategy = quotaStrategy;
    }

    /**
     * @param useStandardQuotaStrategy
     *            the useStandardQuotaStrategy to set
     */
    public void setUseStandardQuotaStrategy(final boolean useStandardQuotaStrategy)
    {
        this.useStandardQuotaStrategy = useStandardQuotaStrategy;
    }

    /**
     * @param standardQuotaPanicThresholdPercent
     *            the standardQuotaPanicThresholdPercent to set
     */
    public void setStandardQuotaPanicThresholdPercent(final int standardQuotaPanicThresholdPercent)
    {
        this.standardQuotaPanicThresholdPercent = standardQuotaPanicThresholdPercent;
    }

    /**
     * @param standardQuotaCleanThresholdPercent
     *            the standardQuotaCleanThresholdPercent to set
     */
    public void setStandardQuotaCleanThresholdPercent(final int standardQuotaCleanThresholdPercent)
    {
        this.standardQuotaCleanThresholdPercent = standardQuotaCleanThresholdPercent;
    }

    /**
     * @param standardQuotaTargetUsagePercent
     *            the standardQuotaTargetUsagePercent to set
     */
    public void setStandardQuotaTargetUsagePercent(final int standardQuotaTargetUsagePercent)
    {
        this.standardQuotaTargetUsagePercent = standardQuotaTargetUsagePercent;
    }

    /**
     * @param standardQuotaMaxUsageBytes
     *            the standardQuotaMaxUsageBytes to set
     */
    public void setStandardQuotaMaxUsageBytes(final int standardQuotaMaxUsageBytes)
    {
        this.standardQuotaMaxUsageBytes = standardQuotaMaxUsageBytes;
    }

    /**
     * @param standardQuotaMaxFileSizeMebiBytes
     *            the standardQuotaMaxFileSizeMebiBytes to set
     */
    public void setStandardQuotaMaxFileSizeMebiBytes(final int standardQuotaMaxFileSizeMebiBytes)
    {
        this.standardQuotaMaxFileSizeMebiBytes = standardQuotaMaxFileSizeMebiBytes;
    }

    /**
     * @param standardQuotaNormalCleanThresholdSeconds
     *            the standardQuotaNormalCleanThresholdSeconds to set
     */
    public void setStandardQuotaNormalCleanThresholdSeconds(final int standardQuotaNormalCleanThresholdSeconds)
    {
        this.standardQuotaNormalCleanThresholdSeconds = standardQuotaNormalCleanThresholdSeconds;
    }

    /**
     * @param cleanerMinFileAgeMillis
     *            the cleanerMinFileAgeMillis to set
     */
    public void setCleanerMinFileAgeMillis(final long cleanerMinFileAgeMillis)
    {
        this.cleanerMinFileAgeMillis = cleanerMinFileAgeMillis;
    }

    /**
     * @param cleanerMaxDeleteWatchCount
     *            the cleanerMaxDeleteWatchCount to set
     */
    public void setCleanerMaxDeleteWatchCount(final Integer cleanerMaxDeleteWatchCount)
    {
        this.cleanerMaxDeleteWatchCount = cleanerMaxDeleteWatchCount;
    }

    /**
     * @param cleanerCronExpression
     *            the cleanerCronExpression to set
     */
    public void setCleanerCronExpression(final String cleanerCronExpression)
    {
        this.cleanerCronExpression = cleanerCronExpression;
    }

    /**
     * @param cleanerStartDelay
     *            the cleanerStartDelay to set
     */
    public void setCleanerStartDelay(final long cleanerStartDelay)
    {
        this.cleanerStartDelay = cleanerStartDelay;
    }

    /**
     * @param cleanerRepeatInterval
     *            the cleanerRepeatInterval to set
     */
    public void setCleanerRepeatInterval(final long cleanerRepeatInterval)
    {
        this.cleanerRepeatInterval = cleanerRepeatInterval;
    }

    /**
     * @param cleanerRepeatCount
     *            the cleanerRepeatCount to set
     */
    public void setCleanerRepeatCount(final int cleanerRepeatCount)
    {
        this.cleanerRepeatCount = cleanerRepeatCount;
    }

    /**
     * @param scheduler
     *            the scheduler to set
     */
    public void setScheduler(final Scheduler scheduler)
    {
        this.scheduler = scheduler;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public CachingContentStore getObject() throws Exception
    {
        final SimpleCache<Key, String> memoryStore = this.cacheFactory.createCache(this.cacheName);

        final ContentCacheImpl cache = new ContentCacheImpl();
        cache.setMemoryStore(memoryStore);
        cache.setCacheRoot(new File(this.cacheRoot));

        final CachingContentStore store = new CachingContentStore();
        store.setBeanName(this.beanName);
        store.setCache(cache);
        store.setBackingStore(this.backingStore);
        store.setCacheOnInbound(this.cacheOnInbound);
        store.setMaxCacheTries(this.maxCacheTries);
        store.setApplicationEventPublisher(this.applicationEventPublisher);

        if (this.quotaStrategy != null)
        {
            store.setQuota(this.quotaStrategy);
        }
        else if (this.useStandardQuotaStrategy)
        {
            final DisposableStandardQuotaStrategy standardQuotaStrategy = new DisposableStandardQuotaStrategy();
            standardQuotaStrategy.setCache(cache);
            standardQuotaStrategy.setPanicThresholdPct(this.standardQuotaPanicThresholdPercent);
            standardQuotaStrategy.setCleanThresholdPct(this.standardQuotaCleanThresholdPercent);
            standardQuotaStrategy.setTargetUsagePct(this.standardQuotaTargetUsagePercent);
            standardQuotaStrategy.setMaxUsageBytes(this.standardQuotaMaxUsageBytes);
            standardQuotaStrategy.setNormalCleanThresholdSec(this.standardQuotaNormalCleanThresholdSeconds);
            standardQuotaStrategy.setMaxFileSizeMB(this.standardQuotaMaxFileSizeMebiBytes);

            final CachedContentCleaner cachedContentCleaner = new CachedContentCleaner();
            cachedContentCleaner.setMinFileAgeMillis(this.cleanerMinFileAgeMillis);
            if (this.cleanerMaxDeleteWatchCount != null)
            {
                cachedContentCleaner.setMaxDeleteWatchCount(this.cleanerMaxDeleteWatchCount);
            }
            cachedContentCleaner.setCache(cache);
            cachedContentCleaner.setUsageTracker(standardQuotaStrategy);
            cachedContentCleaner.setApplicationEventPublisher(this.applicationEventPublisher);
            if (this.beanFactory instanceof ConfigurableBeanFactory)
            {
                ((ConfigurableBeanFactory) this.beanFactory).registerSingleton(this.beanName + "-CachedContentCleaner",
                        cachedContentCleaner);
            }

            store.setQuota(standardQuotaStrategy);
            standardQuotaStrategy.setCleaner(cachedContentCleaner);
            standardQuotaStrategy.init();
            if (this.beanFactory instanceof ConfigurableBeanFactory)
            {
                ((ConfigurableBeanFactory) this.beanFactory).registerSingleton(this.beanName + "-QuotaStrategy", standardQuotaStrategy);
            }
            if (this.beanFactory instanceof DefaultSingletonBeanRegistry)
            {
                ((DefaultSingletonBeanRegistry) this.beanFactory).registerDisposableBean(this.beanName + "-QuotaStrategy",
                        standardQuotaStrategy);
            }

            final JobDetailBean cleanerDetail = new JobDetailBean();
            cleanerDetail.setJobClass(CachedContentCleanupJob.class);
            cleanerDetail.setJobDataAsMap(Collections.<String, Object> singletonMap("cachedContentCleaner", cachedContentCleaner));
            cleanerDetail.setBeanName(this.beanName + "-JobDetail");
            cleanerDetail.setApplicationContext(this.applicationContext);
            cleanerDetail.afterPropertiesSet();

            if (this.beanFactory instanceof ConfigurableBeanFactory)
            {
                ((ConfigurableBeanFactory) this.beanFactory).registerSingleton(this.beanName + "-JobDetail", cleanerDetail);
            }

            AbstractTriggerBean trigger;
            if (this.cleanerCronExpression != null)
            {
                final CronTriggerBean cronTriggerBean = new CronTriggerBean();
                cronTriggerBean.setBeanName(this.beanName + "-JobTrigger");
                cronTriggerBean.setJobDetail(cleanerDetail);
                cronTriggerBean.setCronExpression(this.cleanerCronExpression);
                cronTriggerBean.setStartDelay(this.cleanerStartDelay);
                cronTriggerBean.setScheduler(this.scheduler);
                cronTriggerBean.afterPropertiesSet();
                trigger = cronTriggerBean;
            }
            else
            {
                final TriggerBean triggerBean = new TriggerBean();
                triggerBean.setBeanName(this.beanName + "-JobTrigger");
                triggerBean.setJobDetail(cleanerDetail);
                triggerBean.setStartDelay(this.cleanerStartDelay);
                triggerBean.setRepeatInterval(this.cleanerRepeatInterval);
                triggerBean.setRepeatCount(this.cleanerRepeatCount);
                triggerBean.setScheduler(this.scheduler);
                triggerBean.afterPropertiesSet();
                trigger = triggerBean;
            }

            if (this.beanFactory instanceof ConfigurableBeanFactory)
            {
                ((ConfigurableBeanFactory) this.beanFactory).registerSingleton(trigger.getBeanName(), trigger);
            }

            if (this.beanFactory instanceof DefaultSingletonBeanRegistry)
            {
                ((DefaultSingletonBeanRegistry) this.beanFactory).registerDisposableBean(trigger.getBeanName(), trigger);
            }
        }
        else
        {
            store.setQuota(new UnlimitedQuotaStrategy());
        }

        store.init();

        return store;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public Class<CachingContentStore> getObjectType()
    {
        return CachingContentStore.class;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public boolean isSingleton()
    {
        return true;
    }
}
