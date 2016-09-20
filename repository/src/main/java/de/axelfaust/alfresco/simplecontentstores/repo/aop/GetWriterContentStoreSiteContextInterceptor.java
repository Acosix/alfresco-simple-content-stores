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

import org.alfresco.repo.content.ContentContext;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.content.NodeContentContext;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.site.SiteInfo;
import org.alfresco.service.cmr.site.SiteService;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import de.axelfaust.alfresco.simplecontentstores.repo.store.ContentStoreContext;

/**
 * This interceptor will enricht the currently active {@link ContentStoreContext content store context} with site-related information from
 * the current {@link ContentStore#getWriter(ContentContext) getWriter}-call if possible. The interceptor will check for the presence of a
 * {@link NodeContentContext} and if the affected {@link NodeRef node} is contained in a site, the interceptor will transfer the
 * {@link ContentStoreContext#DEFAULT_ATTRIBUTE_SITE site} and {@link ContentStoreContext#DEFAULT_ATTRIBUTE_SITE_PRESET site preset}
 * identifiers as attributes in the content store context.
 *
 * @author Axel Faust
 */
public class GetWriterContentStoreSiteContextInterceptor implements MethodInterceptor, ApplicationContextAware
{

    protected ApplicationContext applicationContext;

    protected SiteService siteService;

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
        final Method method = invocation.getMethod();

        final Class<?>[] parameterTypes = method.getParameterTypes();
        if ("getWriter".equals(method.getName()) && parameterTypes.length == 1 && parameterTypes[0].isAssignableFrom(ContentContext.class))
        {
            final Object param = invocation.getArguments()[0];
            if (param instanceof NodeContentContext)
            {
                final NodeRef nodeRef = ((NodeContentContext) param).getNodeRef();

                if (this.siteService == null)
                {
                    this.siteService = this.applicationContext.getBean(ServiceRegistry.SITE_SERVICE.getLocalName(), SiteService.class);
                }

                final SiteInfo site = this.siteService.getSite(nodeRef);
                if (site != null)
                {
                    ContentStoreContext.setContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_SITE, site.getShortName());
                    ContentStoreContext.setContextAttribute(ContentStoreContext.DEFAULT_ATTRIBUTE_SITE_PRESET, site.getSitePreset());
                }
            }
        }

        final Object result = invocation.proceed();
        return result;
    }

}
