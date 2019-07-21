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
package de.acosix.alfresco.simplecontentstores.repo.store.context;

import java.util.HashMap;
import java.util.Map;

import org.alfresco.repo.content.ContentContext;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for thread-local data for an ongoing call / use of content store related components. This utility is necessary since not
 * all operations in the ContentStore API have access to the {@link ContentContext} passed in {@link ContentStore#getWriter(ContentContext)
 * getWriter} and {@link ContentStore#getReader(String) getReader} completely lacks that parameter. Additionally, some operations may
 * complete asynchronously when the original context is not even held anywhere in the call stack anymore. This utility class allows a
 * component that may asynchronously need the restore a specific context to obtain a handle on it.
 *
 * @author Axel Faust
 */
public final class ContentStoreContext
{

    public static final String DEFAULT_ATTRIBUTE_SITE = "site";

    public static final String DEFAULT_ATTRIBUTE_SITE_PRESET = "sitePreset";

    private static final Logger LOGGER = LoggerFactory.getLogger(ContentStoreContext.class);

    private static final ThreadLocal<Map<String, Object>> CONTEXT_ATTRIBUTES = new ThreadLocal<>();

    /**
     * This custom functional interface should be used for encapsulating operations that need to be run in an active content store context.
     *
     * This interface is essentially a {@code FunctionalInterface} but does not use the annotation to not force Java 7 on users of this
     * addon (just yet).
     *
     * @author Axel Faust
     */
    public static interface ContentStoreOperation<R>
    {

        /**
         * Executes the operation.
         *
         * @return the result of the operation
         */
        public R execute();

    }

    /**
     * This custom functional interface encapsulates restoration handlers for a previously active content store context.
     *
     * This interface is essentially a {@code FunctionalInterface} but does not use the annotation to not force Java 7 on users of this
     * addon (just yet).
     *
     * @author Axel Faust
     */
    public static interface ContentStoreContextRestorator<R>
    {

        /**
         * Restores the previous content store context and executes an operation within that context.
         *
         * @param operation
         *            the operation to execute inside the restored context
         * @return the result of the operation
         */
        public R withRestoredContext(ContentStoreOperation<R> operation);

    }

    /**
     * Retrieves the value of a context attribute from the currently active content store context.
     *
     * @param key
     *            the key to the attribute value
     * @return the value of the attribute or {@code null} if it has not been set
     */
    public static Object getContextAttribute(final String key)
    {
        final Map<String, Object> currentMap = CONTEXT_ATTRIBUTES.get();
        final Object result = currentMap != null ? currentMap.get(key) : null;
        return result;
    }

    /**
     * Retrieves the value of a context attribute from the currently active content store context.
     *
     * @param key
     *            the key to the attribute value
     * @param value
     *            the value to set
     *
     * @throws IllegalStateException
     *             if there is no currently active content store context in the current thread context
     */
    public static void setContextAttribute(final String key, final Object value)
    {
        final Map<String, Object> currentMap = CONTEXT_ATTRIBUTES.get();
        if (currentMap == null)
        {
            throw new IllegalStateException("No content store context is currently active");
        }
        LOGGER.debug("Setting context attribute {} to {}", key, value);
        currentMap.put(key, value);
    }

    /**
     * Executes an operation within a new content store context. Code in the provided operation can call
     * {@link #setContextAttribute(String, Object) setContextAttribute} and be sure no {@link IllegalStateException} will be thrown.
     *
     * @param <R>
     *            the return type of the operation to execute
     * @param operation
     *            the operation to execute
     * @return the result of the operation
     */
    public static <R> R executeInNewContext(final ContentStoreOperation<R> operation)
    {
        final Map<String, Object> oldMap = CONTEXT_ATTRIBUTES.get();
        final Map<String, Object> newMap = new HashMap<>();
        CONTEXT_ATTRIBUTES.set(newMap);
        try
        {
            LOGGER.trace("Running operation {} in new context", operation);
            final R result = operation.execute();
            return result;
        }
        catch (final Exception ex)
        {
            if (ex instanceof RuntimeException)
            {
                throw (RuntimeException) ex;
            }
            LOGGER.debug("Unhandled exception in content store context operation", ex);
            throw new ContentIOException("Unhandled error in content store operation", ex);
        }
        finally
        {
            LOGGER.trace("Leaving context");
            CONTEXT_ATTRIBUTES.set(oldMap);
        }
    }

    /**
     * Obtains a handle to restore the current content store context state at a later point in time, e.g. to complete some asynchronous work
     * outside of the original content store context. Any attributes {@link #setContextAttribute(String, Object) set / modified} after this
     * operation is called will not be reflected in the restored content store context state.
     *
     * @param <R>
     *            the return type of any operation passed to the restoration handle
     * @return the restoration handle
     *
     * @throws IllegalStateException
     *             if there is no currently active content store context in the current thread context
     */
    public static <R> ContentStoreContextRestorator<R> getContextRestorationHandle()
    {
        final Map<String, Object> savedContextAttributes;
        {
            final Map<String, Object> currentMap = CONTEXT_ATTRIBUTES.get();
            if (currentMap == null)
            {
                throw new IllegalStateException("No content store context is currently active");
            }
            savedContextAttributes = new HashMap<>(currentMap);
        }

        // could have used Lambda in Java 8 but don't want to force it on addon users
        final ContentStoreContextRestorator<R> restorationHandle = operation -> {
            final Map<String, Object> oldMap = CONTEXT_ATTRIBUTES.get();
            final Map<String, Object> newMap = new HashMap<>(savedContextAttributes);
            CONTEXT_ATTRIBUTES.set(newMap);
            try
            {
                LOGGER.trace("Running operation {} in restored context {}", operation, savedContextAttributes);
                final R result = operation.execute();
                return result;
            }
            catch (final Exception ex)
            {
                if (ex instanceof RuntimeException)
                {
                    throw (RuntimeException) ex;
                }
                LOGGER.debug("Unhandled exception in content store context operation", ex);
                throw new ContentIOException("Unhandled error in content store operation", ex);
            }
            finally
            {
                LOGGER.trace("Leaving restored context");
                CONTEXT_ATTRIBUTES.set(oldMap);
            }
        };

        return restorationHandle;
    }
}
