/*
 * Copyright 2017 - 2021 Acosix GmbH
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
package de.acosix.alfresco.simplecontentstores.repo.store;

import org.alfresco.repo.content.caching.quota.StandardQuotaStrategy;
import org.springframework.beans.factory.DisposableBean;

/**
 * A simple specialization to map standard Spring destroy callback to shutdown.
 *
 * @author Axel Faust
 */
public class DisposableStandardQuotaStrategy extends StandardQuotaStrategy implements DisposableBean
{

    /**
     * {@inheritDoc}
     */
    @Override
    public void destroy() throws Exception
    {
        this.shutdown();
    }

}
