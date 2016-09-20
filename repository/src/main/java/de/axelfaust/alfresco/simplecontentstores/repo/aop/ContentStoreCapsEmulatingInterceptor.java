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
package de.axelfaust.alfresco.simplecontentstores.repo.aop;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.alfresco.repo.content.AbstractContentStore;
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

        private final List<TenantDeployer> tenantAwareContentStores;

        protected TenantRoutingContentStoreMultiDispatcher(final List<TenantDeployer> tenantAwareContentStores)
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

            final List<TenantDeployer> tenantAwareContentStores = new ArrayList<>();
            final Map<String, AbstractContentStore> beansOfType = this.applicationContext.getBeansOfType(AbstractContentStore.class);
            for (final AbstractContentStore contentStore : beansOfType.values())
            {
                if (contentStore instanceof ContentStoreCaps)
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

            if (tenantAwareContentStores.isEmpty())
            {
                result = null;
            }
            else if (tenantAwareContentStores.size() == 1)
            {
                result = tenantAwareContentStores.get(0);
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
