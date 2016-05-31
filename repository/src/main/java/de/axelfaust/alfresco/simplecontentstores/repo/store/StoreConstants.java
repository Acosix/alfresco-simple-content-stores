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
package de.axelfaust.alfresco.simplecontentstores.repo.store;

import org.alfresco.repo.content.ContentStore;

/**
 * @author Axel Faust
 */
public interface StoreConstants
{

    /**
     * Store protocol to be used when the specific protocol not matter to a client and may be substituted with the actual protocol by a
     * store handling a content URL. This can be useful in supporting {@link ContentStore#exists(String) existence checks} for generic URLs,
     * e.g. by a routing store.
     */
    String WILDCARD_PROTOCOL = "dummy-wildcard-store-protocol";

}
