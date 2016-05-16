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
package de.axelfaust.alfresco.simplecontentstores.repo.beans;

import org.alfresco.repo.content.ContentLimitProvider;
import org.alfresco.repo.content.filestore.FileContentStore;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * @author Axel Faust
 */
public class FileContentStoreFactoryBean implements FactoryBean<FileContentStore>, ApplicationContextAware
{

    protected ApplicationContext applicationContext;

    protected String rootDirectory;

    protected boolean readOnly;

    protected boolean allowRandomAccess;

    protected boolean deleteEmptyDirs;

    protected ContentLimitProvider contentLimitProvider;

    /**
     * @param rootDirectory
     *            the rootDirectory to set
     */
    public void setRootDirectory(final String rootDirectory)
    {
        this.rootDirectory = rootDirectory;
    }

    /**
     * @param readOnly
     *            the readOnly to set
     */
    public void setReadOnly(final boolean readOnly)
    {
        this.readOnly = readOnly;
    }

    /**
     * @param allowRandomAccess
     *            the allowRandomAccess to set
     */
    public void setAllowRandomAccess(final boolean allowRandomAccess)
    {
        this.allowRandomAccess = allowRandomAccess;
    }

    /**
     * @param deleteEmptyDirs
     *            the deleteEmptyDirs to set
     */
    public void setDeleteEmptyDirs(final boolean deleteEmptyDirs)
    {
        this.deleteEmptyDirs = deleteEmptyDirs;
    }

    /**
     * @param contentLimitProvider
     *            the contentLimitProvider to set
     */
    public void setContentLimitProvider(final ContentLimitProvider contentLimitProvider)
    {
        this.contentLimitProvider = contentLimitProvider;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void setApplicationContext(final ApplicationContext applicationContext)
    {
        this.applicationContext = applicationContext;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public FileContentStore getObject() throws Exception
    {
        final FileContentStore store = new FileContentStore(this.applicationContext, this.rootDirectory);

        store.setAllowRandomAccess(this.allowRandomAccess);
        store.setDeleteEmptyDirs(this.deleteEmptyDirs);
        store.setReadOnly(this.readOnly);
        store.setContentLimitProvider(this.contentLimitProvider);

        return store;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public Class<FileContentStore> getObjectType()
    {
        return FileContentStore.class;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public boolean isSingleton()
    {
        return true;
    }
}
