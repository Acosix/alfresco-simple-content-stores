/*
 * Copyright 2017 - 2022 Acosix GmbH
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

import org.alfresco.repo.content.ContentContext;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;

/**
 * @author Axel Faust
 */
public interface ContentStoreContextInitializer
{

    /**
     * Initialise attributes in the currently active {@link ContentStoreContext content store context} from a content context. This
     * operation requires that a content store context has been
     * {@link ContentStoreContext#executeInNewContext(de.acosix.alfresco.simplecontentstores.repo.store.context.ContentStoreContext.ContentStoreOperation)
     * initialised} for the current thread.
     *
     * This operation is typically called whenever a content store context needs to be initialised for an
     * {@link ContentStore#getWriter(ContentContext) instantiation of a content writer}.
     *
     * @param context
     *            the context from which to initialise the currently active content store context
     */
    public void initialize(ContentContext context);

    /**
     * Initialise attributes in the currently active {@link ContentStoreContext content store context} from a content context. This
     * operation requires that a content store context has been
     * {@link ContentStoreContext#executeInNewContext(de.acosix.alfresco.simplecontentstores.repo.store.context.ContentStoreContext.ContentStoreOperation)
     * initialised} for the current thread.
     *
     * This operation is typically called whenever a content store context needs to be initialised for an
     * {@link ContentService#getReader(NodeRef, QName) instantiation of a content reader}, but <b>only</b> when using the proper public
     * service bean.
     *
     * @param node
     *            the reference of the node for which the reader to a content property is to be retrieved
     * @param propertyQName
     *            the qualified name of the content property for which to retrieve the reader
     */
    public void initialize(NodeRef node, QName propertyQName);
}
