package de.acosix.alfresco.simplecontentstores.repo.aop;

import java.lang.reflect.Method;

import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.PropertyCheck;
import org.alfresco.util.transaction.TransactionSupportUtil;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.InitializingBean;

import de.acosix.alfresco.simplecontentstores.repo.dao.ContentUrlConsistencyHandler;

/**
 * This interceptor handles calls to the public {@link ContentService} bean, specifically ensuring that a transactional listener is bound
 * sufficiently early in operations that involve {@link ContentService#getWriter(NodeRef, QName, boolean) getWriter} to ensure the content
 * URL-related cache is properly invalidated at the end of the transaction. This is necessary to deal with a design/implementation bug in
 * standard Alfresco Content Services
 * which does not invalidate cache entries when:
 * <ol>
 * <li>content URLs are marked as orphaned</li>
 * <li>content URLs are deleted</li>
 * </ol>
 *
 * @author Axel Faust
 */
public class ContentServiceUrlConsistencyInterceptor implements MethodInterceptor, InitializingBean
{

    protected ContentUrlConsistencyHandler contentUrlConsistencyHandler;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "contentUrlConsistencyHandler", this.contentUrlConsistencyHandler);
    }

    /**
     * @param contentUrlConsistencyHandler
     *     the contentUrlConsistencyHandler to set
     */
    public void setContentUrlConsistencyHandler(final ContentUrlConsistencyHandler contentUrlConsistencyHandler)
    {
        this.contentUrlConsistencyHandler = contentUrlConsistencyHandler;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public Object invoke(final MethodInvocation invocation) throws Throwable
    {
        final Method method = invocation.getMethod();
        final Class<?> declaringClass = method.getDeclaringClass();

        if (ContentService.class.isAssignableFrom(declaringClass) && "getWriter".equals(method.getName())
                && TransactionSupportUtil.isActualTransactionActive())
        {
            this.contentUrlConsistencyHandler.ensureListenerIsBound();
        }
        return invocation.proceed();
    }
}
