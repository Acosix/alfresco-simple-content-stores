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
package de.acosix.alfresco.simplecontentstores.repo.aop;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.alfresco.repo.content.AbstractContentStore;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.content.ContentStoreCaps;
import org.alfresco.repo.tenant.TenantDeployer;
import org.alfresco.repo.tenant.TenantRoutingContentStore;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * This interceptor is necessary to facade the global content store (which may not implement {@link ContentStoreCaps} in order to fulfill
 * the interface contract and expose any tenant-aware content stores within the configured hierarchy. This interceptor uses the
 * {@link ApplicationContext} to lookup all {@link AbstractContentStore}-derived beans and checks them for compliance to the expected
 * interfaces. If multiple beans are found they are grouped via a simple facade POJO and all operations that may be of interest to all
 * instances are dispatched to all, while {@link TenantRoutingContentStore#getRootLocation() getRootLocation} will only be dispatched until
 * the first instance yields a non-{@code null} result.
 *
 * @author Axel Faust
 */
public class ContentStoreCapsEmulatingInterceptor implements MethodInterceptor, ApplicationContextAware
{

    protected static class TenantRoutingContentStoreMultiDispatcher implements TenantRoutingContentStore
    {

        private final Collection<TenantDeployer> tenantAwareContentStores;

        protected TenantRoutingContentStoreMultiDispatcher(final Collection<TenantDeployer> tenantAwareContentStores)
        {
            this.tenantAwareContentStores = tenantAwareContentStores;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onEnableTenant()
        {
            for (final TenantDeployer tenantAwareContentStore : this.tenantAwareContentStores)
            {
                tenantAwareContentStore.onEnableTenant();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onDisableTenant()
        {
            for (final TenantDeployer tenantAwareContentStore : this.tenantAwareContentStores)
            {
                tenantAwareContentStore.onDisableTenant();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void init()
        {
            for (final TenantDeployer tenantAwareContentStore : this.tenantAwareContentStores)
            {
                tenantAwareContentStore.init();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void destroy()
        {
            for (final TenantDeployer tenantAwareContentStore : this.tenantAwareContentStores)
            {
                tenantAwareContentStore.destroy();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getRootLocation()
        {
            String rootLocation = null;
            for (final TenantDeployer tenantAwareContentStore : this.tenantAwareContentStores)
            {
                if (tenantAwareContentStore instanceof TenantRoutingContentStore)
                {
                    rootLocation = ((TenantRoutingContentStore) tenantAwareContentStore).getRootLocation();
                }

                if (rootLocation != null)
                {
                    break;
                }
            }

            return rootLocation;
        }

    }

    protected ApplicationContext applicationContext;

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
    public Object invoke(final MethodInvocation invocation) throws Throwable
    {
        Object result;
        final Method method = invocation.getMethod();
        if (ContentStoreCaps.class.isAssignableFrom(method.getDeclaringClass()) && method.getParameterTypes().length == 0
                && ("getTenantRoutingContentStore".equals(method.getName()) || "getTenantDeployer".equals(method.getName())))
        {
            final boolean tenantRoutingContentStoreRequired = "getTenantRoutingContentStore".equals(method.getName());

            final Set<TenantDeployer> tenantAwareContentStores = new LinkedHashSet<>();
            final Map<String, ContentStore> beansOfType = this.applicationContext.getBeansOfType(ContentStore.class, false, false);
            for (final ContentStore contentStore : beansOfType.values())
            {
                if (!Proxy.isProxyClass(contentStore.getClass()))
                {
                    if (tenantRoutingContentStoreRequired && contentStore instanceof TenantRoutingContentStore)
                    {
                        tenantAwareContentStores.add((TenantRoutingContentStore) contentStore);
                    }
                    else if (contentStore instanceof ContentStoreCaps)
                    {
                        final ContentStoreCaps contentStoreCaps = (ContentStoreCaps) contentStore;
                        final TenantDeployer tenantAwareContentStore = tenantRoutingContentStoreRequired
                                ? contentStoreCaps.getTenantRoutingContentStore() : contentStoreCaps.getTenantDeployer();
                        if (tenantAwareContentStore != null)
                        {
                            tenantAwareContentStores.add(tenantAwareContentStore);
                        }
                    }
                }
            }

            if (tenantAwareContentStores.isEmpty())
            {
                result = null;
            }
            else if (tenantAwareContentStores.size() == 1)
            {
                result = tenantAwareContentStores.iterator().next();
            }
            else
            {
                result = new TenantRoutingContentStoreMultiDispatcher(tenantAwareContentStores);
            }
        }
        else
        {
            result = invocation.proceed();
        }

        return result;
    }

}
