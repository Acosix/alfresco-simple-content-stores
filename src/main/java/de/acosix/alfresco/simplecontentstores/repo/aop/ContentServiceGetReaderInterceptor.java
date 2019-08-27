package de.acosix.alfresco.simplecontentstores.repo.aop;

import java.lang.reflect.Method;
import java.util.LinkedList;

import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import de.acosix.alfresco.simplecontentstores.repo.store.context.ContentStoreContextInitializer;

/**
 * This interceptor handles calls to the public {@link ContentService} bean, specifically capturing the parameters of a
 * {@link ContentService#getReader(org.alfresco.service.cmr.repository.NodeRef, org.alfresco.service.namespace.QName) getReader} invocation
 * in a thread-local data structure for later use in
 * {@link ContentStoreContextInitializer#initialize(org.alfresco.service.cmr.repository.NodeRef, org.alfresco.service.namespace.QName)
 * content store context initialisation}.
 *
 * @author Axel Faust
 */
public class ContentServiceGetReaderInterceptor implements MethodInterceptor
{

    private static class CallHolder
    {

        private final NodeRef node;

        private final QName propertyQName;

        private boolean consumed = false;

        protected CallHolder(final NodeRef node, final QName propertyQName)
        {
            this.node = node;
            this.propertyQName = propertyQName;
        }

        /**
         * @return the consumed
         */
        public boolean isConsumed()
        {
            return this.consumed;
        }

        /**
         * @param consumed
         *            the consumed to set
         */
        public void setConsumed(final boolean consumed)
        {
            this.consumed = consumed;
        }

        /**
         * @return the node
         */
        public NodeRef getNode()
        {
            return this.node;
        }

        /**
         * @return the propertyQName
         */
        public QName getPropertyQName()
        {
            return this.propertyQName;
        }

    }

    private static final ThreadLocal<LinkedList<CallHolder>> GET_READER_CONTEXT_STACK = new ThreadLocal<LinkedList<CallHolder>>()
    {

        /**
         * {@inheritDoc}
         *
         */
        @Override
        protected LinkedList<CallHolder> initialValue()
        {
            return new LinkedList<>();
        }
    };

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public Object invoke(final MethodInvocation invocation) throws Throwable
    {
        final Method method = invocation.getMethod();
        final Class<?>[] parameterTypes = method.getParameterTypes();
        final Class<?> declaringClass = method.getDeclaringClass();

        Object result;
        if (ContentService.class.isAssignableFrom(declaringClass) && "getReader".equals(method.getName()) && parameterTypes.length == 2
                && parameterTypes[0].equals(NodeRef.class) && parameterTypes[1].equals(QName.class))
        {
            final Object[] arguments = invocation.getArguments();
            final NodeRef node = (NodeRef) arguments[0];
            final QName propertyQName = (QName) arguments[1];

            GET_READER_CONTEXT_STACK.get().addLast(new CallHolder(node, propertyQName));
            try
            {
                result = invocation.proceed();
            }
            finally
            {
                GET_READER_CONTEXT_STACK.get().removeLast();
            }
        }
        else
        {
            result = invocation.proceed();
        }

        return result;
    }

    /**
     * Marks the context of the most recent call to {@link ContentService#getReader(NodeRef, QName) getReader} as consumed, so as to avoid
     * the context being accidentally reused in any nested calls which do not properly use the public {@link ContentService} bean.
     */
    public static void markCurrentContextConsumed()
    {
        final LinkedList<CallHolder> context = GET_READER_CONTEXT_STACK.get();
        if (!context.isEmpty())
        {
            context.getLast().setConsumed(true);
        }
    }

    /**
     * Retrieves the (most recent) node for which a call to {@link ContentService#getReader(NodeRef, QName) getReader} is currently active
     * in the current thread, if present at all.
     *
     * @return the node of the {@code getReader} call, or {@code null} if no such call is active in the current thread or has already been
     *         {@link #markCurrentContextConsumed() marked as consumed}
     */
    public static NodeRef getCurrentGetReaderContextNode()
    {
        final LinkedList<CallHolder> context = GET_READER_CONTEXT_STACK.get();
        NodeRef node = null;
        if (!context.isEmpty() && !context.getLast().isConsumed())
        {
            node = context.getLast().getNode();
        }
        return node;
    }

    /**
     * Retrieves the (most recent) qualified property name for which a call to {@link ContentService#getReader(NodeRef, QName) getReader} is
     * currently active in the current thread, if present at all.
     *
     * @return the qualified property name of the {@code getReader} call, or {@code null} if no such call is active in the current thread or
     *         has already been {@link #markCurrentContextConsumed() marked as consumed}
     */
    public static QName getCurrentGetReaderContextPropertyQName()
    {
        final LinkedList<CallHolder> context = GET_READER_CONTEXT_STACK.get();
        QName propertyQName = null;
        if (!context.isEmpty() && !context.getLast().isConsumed())
        {
            propertyQName = context.getLast().getPropertyQName();
        }
        return propertyQName;
    }
}
