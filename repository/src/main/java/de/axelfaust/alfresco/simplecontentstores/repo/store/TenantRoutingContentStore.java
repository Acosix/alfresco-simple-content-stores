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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.alfresco.repo.content.ContentContext;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.tenant.TenantUtil;
import org.alfresco.util.PropertyCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Axel Faust
 */
public class TenantRoutingContentStore extends CommonRoutingContentStore<Void>
{

    private static final Logger LOGGER = LoggerFactory.getLogger(TenantRoutingContentStore.class);

    protected Map<String, ContentStore> storeByTenant;

    protected transient List<ContentStore> allStores;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        super.afterPropertiesSet();

        this.afterPropertiesSet_setupStoreData();
    }

    /**
     * @param storeByTenant
     *            the storeByTenant to set
     */
    public void setStoreByTenant(final Map<String, ContentStore> storeByTenant)
    {
        this.storeByTenant = storeByTenant;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected List<ContentStore> getAllStores()
    {
        return Collections.unmodifiableList(this.allStores);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected ContentStore selectWriteStore(final ContentContext ctx)
    {
        final ContentStore store;

        if (this.isRoutable(ctx))
        {
            final String currentDomain = TenantUtil.getCurrentDomain();

            ContentStore valueStore = null;
            if (currentDomain != null && !currentDomain.trim().isEmpty())
            {
                valueStore = this.storeByTenant.get(currentDomain);
                LOGGER.debug("Selecting store for tenant {} to write {}", currentDomain, ctx);
            }
            else
            {
                LOGGER.debug("Selecting fallback store to write {}", ctx);
                valueStore = this.fallbackStore;
            }

            store = valueStore;
        }
        else
        {
            LOGGER.debug("Selecting fallback store to write {}", ctx);
            store = this.fallbackStore;
        }

        return store;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean isRoutable(final ContentContext ctx)
    {
        return true;
    }

    private void afterPropertiesSet_setupStoreData()
    {
        PropertyCheck.mandatory(this, "storeByTenant", this.storeByTenant);

        this.allStores = new ArrayList<>();

        for (final ContentStore store : this.storeByTenant.values())
        {
            if (!this.allStores.contains(store))
            {
                this.allStores.add(store);
            }
        }

        if (!this.allStores.contains(this.fallbackStore))
        {
            this.allStores.add(this.fallbackStore);
        }
    }
}
