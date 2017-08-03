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
package de.axelfaust.alfresco.simplecontentstores.repo.store.facade;

import java.util.Locale;

import org.alfresco.service.cmr.repository.ContentAccessor;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.ContentStreamListener;
import org.alfresco.util.ParameterCheck;

/**
 * @author Axel Faust, <a href="http://acosix.de">Acosix GmbH</a>
 */
public class ContentAccessorFacade<CA extends ContentAccessor> implements ContentAccessor
{

    protected CA delegate;

    public ContentAccessorFacade(final CA delegate)
    {
        ParameterCheck.mandatory("delegate", delegate);
        this.delegate = delegate;
    }

    protected ContentAccessorFacade()
    {
        // NO-OP
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public boolean isChannelOpen()
    {
        this.ensureDelegate();
        return this.delegate.isChannelOpen();
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void addListener(final ContentStreamListener listener)
    {
        this.ensureDelegate();
        this.delegate.addListener(listener);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public long getSize()
    {
        this.ensureDelegate();
        return this.delegate.getSize();
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public ContentData getContentData()
    {
        this.ensureDelegate();
        return this.delegate.getContentData();
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public String getContentUrl()
    {
        this.ensureDelegate();
        return this.delegate.getContentUrl();
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public String getMimetype()
    {
        this.ensureDelegate();
        return this.delegate.getMimetype();
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void setMimetype(final String mimetype)
    {
        this.ensureDelegate();
        this.delegate.setMimetype(mimetype);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public String getEncoding()
    {
        this.ensureDelegate();
        return this.delegate.getEncoding();
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void setEncoding(final String encoding)
    {
        this.ensureDelegate();
        this.delegate.setEncoding(encoding);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public Locale getLocale()
    {
        this.ensureDelegate();
        return this.delegate.getLocale();
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void setLocale(final Locale locale)
    {
        this.ensureDelegate();
        this.delegate.setLocale(locale);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
        final String string = this.getClass().getSimpleName() + "- " + String.valueOf(this.delegate);
        return string;
    }

    protected final void ensureDelegate()
    {
        if (this.delegate == null)
        {
            this.delegate = this.initDelegate();
        }
    }

    protected CA initDelegate()
    {
        throw new UnsupportedOperationException(
                "Cannot lazily initialise the delegate - sub-class must implement this for specific use case");
    }
}
