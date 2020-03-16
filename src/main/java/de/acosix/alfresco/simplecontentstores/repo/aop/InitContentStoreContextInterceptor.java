/*
 * Copyright 2017 - 2020 Acosix GmbH
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
import java.util.Collection;

import org.alfresco.repo.content.ContentContext;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import de.acosix.alfresco.simplecontentstores.repo.store.context.ContentStoreContext;
import de.acosix.alfresco.simplecontentstores.repo.store.context.ContentStoreContextInitializer;

/**
 * This interceptor initialises the {@link ContentStoreContext} on any call to the root {@link ContentStore content store} and thus
 * guarantees an active context is present for use within the configured chain of content stores.
 *
 * @author Axel Faust
 */
public class InitContentStoreContextInterceptor implements MethodInterceptor, ApplicationContextAware
{

    private static final Logger LOGGER = LoggerFactory.getLogger(InitContentStoreContextInterceptor.class);

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
        return ContentStoreContext.executeInNewContext(() -> {

            final Method method = invocation.getMethod();
            final Class<?>[] parameterTypes = method.getParameterTypes();
            final Class<?> declaringClass = method.getDeclaringClass();

            if (ContentStore.class.isAssignableFrom(declaringClass))
            {
                final Object[] arguments = invocation.getArguments();
                final Collection<ContentStoreContextInitializer> initializers = InitContentStoreContextInterceptor.this.applicationContext
                        .getBeansOfType(ContentStoreContextInitializer.class, false, false).values();

                if ("getWriter".equals(method.getName()) && parameterTypes.length == 1
                        && ContentContext.class.isAssignableFrom(parameterTypes[0]))
                {
                    final ContentContext context = (ContentContext) arguments[0];

                    for (final ContentStoreContextInitializer initializer : initializers)
                    {
                        initializer.initialize(context);
                    }
                }
                else if ("getReader".equals(method.getName()) && parameterTypes.length == 1 && String.class.equals(parameterTypes[0]))
                {
                    final NodeRef node = ContentServiceGetReaderInterceptor.getCurrentGetReaderContextNode();
                    final QName propertyQName = ContentServiceGetReaderInterceptor.getCurrentGetReaderContextPropertyQName();

                    if (node != null && propertyQName != null)
                    {
                        ContentServiceGetReaderInterceptor.markCurrentContextConsumed();

                        for (final ContentStoreContextInitializer initializer : initializers)
                        {
                            initializer.initialize(node, propertyQName);
                        }
                    }
                }
            }

            try
            {
                return invocation.proceed();
            }
            catch (final Throwable ex)
            {
                // only log as debug, since our rethrown exception should be properly logged by the top caller (web script or other API)
                // (Some APIs, such as Alfresco Public ReST API, do horrible jobs of logging though)
                LOGGER.debug("Error during call on ContentStore API", ex);
                throw new ContentIOException("Error during call on ContentStore API", ex);
            }
        });
    }

}
