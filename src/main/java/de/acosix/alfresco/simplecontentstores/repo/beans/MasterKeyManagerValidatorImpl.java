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
package de.acosix.alfresco.simplecontentstores.repo.beans;

import org.alfresco.util.PropertyCheck;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationEvent;
import org.springframework.extensions.surf.util.AbstractLifecycleBean;

import de.acosix.alfresco.simplecontentstores.repo.store.encrypted.InternalMasterKeyManager;

/**
 *
 * @author Axel Faust
 */
public class MasterKeyManagerValidatorImpl extends AbstractLifecycleBean implements InitializingBean
{

    protected InternalMasterKeyManager masterKeyManager;

    /**
     * @param masterKeyManager
     *            the masterKeyManager to set
     */
    public void setMasterKeyManager(final InternalMasterKeyManager masterKeyManager)
    {
        this.masterKeyManager = masterKeyManager;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "masterKeyManager", this.masterKeyManager);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onBootstrap(final ApplicationEvent event)
    {
        this.masterKeyManager.onStartup();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onShutdown(final ApplicationEvent event)
    {
        // NO-OP
    }
}
