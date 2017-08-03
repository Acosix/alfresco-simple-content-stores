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
package de.acosix.alfresco.simplecontentstores.repo.store.context;

import org.alfresco.repo.content.ContentContext;

/**
 * @author Axel Faust
 */
public interface ContentStoreContextInitializer
{

    /**
     * Initialize attributes in the currently active {@link ContentStoreContext content store context} from a content context. This
     * operation requires that a content store context has been
     * {@link ContentStoreContext#executeInNewContext(de.axelfaust.alfresco.simplecontentstores.repo.store.context.ContentStoreContext.ContentStoreOperation)
     * initialized} for the current thread.
     *
     * @param context
     *            the context from which to initialize the currently active content store context
     */
    public void initialize(ContentContext context);

}
