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
package de.acosix.alfresco.simplecontentstores.repo.store.file;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.alfresco.repo.content.AbstractContentStore;
import org.alfresco.repo.content.transform.ContentTransformer;
import org.alfresco.repo.content.transform.TransformerDebug;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.MimetypeService;
import org.alfresco.service.cmr.repository.TransformationOptions;
import org.alfresco.util.PropertyCheck;
import org.alfresco.util.TempFileProvider;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import de.acosix.alfresco.simplecontentstores.repo.store.facade.ContentReaderFacade;

/**
 * This implementation of a content store is capable of providing "dummy" (generic) documents for read-access to content URLs. This can be
 * used on test environments to allow use of fully cloned production database while not having to fully clone the content store e.g. when
 * tests do not require it or some data may be too sensitive to be cloned.
 *
 * The implementation may use either specifically provided "dummy" files or use existing "dummy" files included in Alfresco for
 * transformation checks. It optionally supports to dynamically generate a "dummy" file via
 * registered content transformers if no sample file has been provided for a specific mimetype. If neither a sample file has been provided
 * nor a transformation succeeds in providing one, the {@link ContentReader} provided by the store will fail with a
 * {@link ContentIOException}.
 *
 * @author Axel Faust
 */
public class DummyFallbackContentStore extends AbstractContentStore implements ApplicationContextAware, InitializingBean
{

    private static final long LONG_LIFE_DURATOIN = 24 * 3600L * 1000L;

    private static final Logger LOGGER = LoggerFactory.getLogger(DummyFallbackContentStore.class);

    protected ApplicationContext applicationContext;

    protected MimetypeService mimetypeService;

    protected List<String> dummyFilePaths;

    protected List<String> transformationCandidateSourceMimetypes;

    // resolved lazily to avoid circular dependencies
    protected ContentService contentService;

    protected final Map<String, File> tempFileCache = new ConcurrentHashMap<>();

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "mimetypeService", this.mimetypeService);
        PropertyCheck.mandatory(this, "dummyFilePaths", this.dummyFilePaths);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException
    {
        this.applicationContext = applicationContext;
    }

    /**
     * @param mimetypeService
     *            the mimetypeService to set
     */
    public void setMimetypeService(final MimetypeService mimetypeService)
    {
        this.mimetypeService = mimetypeService;
    }

    /**
     * @param dummyFilePaths
     *            the dummyFilePaths to set
     */
    public void setDummyFilePaths(final List<String> dummyFilePaths)
    {
        this.dummyFilePaths = dummyFilePaths;
    }

    /**
     * @param transformationCandidateSourceMimetypes
     *            the transformationCandidateSourceMimetypes to set
     */
    public void setTransformationCandidateSourceMimetypes(final List<String> transformationCandidateSourceMimetypes)
    {
        this.transformationCandidateSourceMimetypes = transformationCandidateSourceMimetypes;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public boolean isWriteSupported()
    {
        return false;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public ContentReader getReader(final String contentUrl)
    {
        final DummyFallbackContentReader reader = new DummyFallbackContentReader(contentUrl);
        return reader;
    }

    protected boolean isDummyFileAvailable(final String mimetype)
    {
        final Resource resource = this.getDummyFileResource(mimetype);
        final boolean available = resource != null;
        LOGGER.debug("Dummy file is available for mimetype {}: {}", mimetype, available);
        return available;
    }

    protected Resource getDummyFileResource(final String mimetype)
    {
        final String extension = this.mimetypeService.getExtension(mimetype);
        Resource resource = null;
        final List<String> pathsToSearch = new ArrayList<>(this.dummyFilePaths);
        Collections.reverse(pathsToSearch);

        final DefaultResourceLoader resourceLoader = new DefaultResourceLoader();

        for (final String path : pathsToSearch)
        {
            resource = resourceLoader.getResource(path + "/dummy." + extension);
            if (resource != null)
            {
                if (resource.exists())
                {
                    break;
                }
                // nope'd
                resource = null;
            }
        }
        LOGGER.trace("Found dummy file resource {} for extension {}", resource, extension);
        return resource;
    }

    protected boolean isTransformerDebugFileAvailable(final String mimetype)
    {
        final URL url = this.getTransformerDebugFileURL(mimetype);
        final boolean available = url != null;
        LOGGER.debug("TransformerDebug file is available for mimetype {}: {}", mimetype, available);
        return available;
    }

    protected URL getTransformerDebugFileURL(final String mimetype)
    {
        final String extension = this.mimetypeService.getExtension(mimetype);
        final URL url = TransformerDebug.class.getClassLoader().getResource("quick/quick." + extension);

        LOGGER.trace("Found TransformerDebug file URL {} for extension {}", url, extension);
        return url;
    }

    protected boolean isLazilyTransformedDummyFileAvailable(final String mimetype)
    {
        boolean lazyTransformationAvailable = false;

        if (this.transformationCandidateSourceMimetypes != null)
        {
            if (this.contentService == null)
            {
                this.contentService = this.applicationContext.getBean("contentService", ContentService.class);
            }

            for (final String sourceMimetype : this.transformationCandidateSourceMimetypes)
            {
                if (this.isDummyFileAvailable(sourceMimetype))
                {
                    final ContentTransformer transformer = this.contentService.getTransformer(sourceMimetype, mimetype);
                    lazyTransformationAvailable = transformer != null;

                    if (lazyTransformationAvailable)
                    {
                        LOGGER.trace("Found transformation from {} to {}", sourceMimetype, mimetype);
                        break;
                    }
                }
                else
                {
                    LOGGER.trace("No dummy file provided for source mimetype {}", mimetype);
                }
            }
        }

        LOGGER.debug("Lazily transformable dummy file is likely available for mimetype {}: {}", mimetype, lazyTransformationAvailable);
        return lazyTransformationAvailable;
    }

    protected class DummyFallbackContentReader extends ContentReaderFacade
    {

        protected String contentURL;

        protected String mimetype;

        protected String encoding;

        protected Locale locale;

        public DummyFallbackContentReader(final String contentURL)
        {
            super();
            this.contentURL = contentURL;
        }

        /**
         *
         * {@inheritDoc}
         */
        @Override
        public boolean exists()
        {
            boolean exists;
            if (this.delegate != null)
            {
                exists = this.delegate.exists();
            }
            else
            {
                final String mimetype = this.getMimetype();
                /*
                 * We would love to make mimetype != null a requirement, but unfortunately facades (like AggregatingContentStore) check
                 * exists() before mimetype could be set on reader.
                 * This means a facade may assume a dummy exists due to potentially incorrect result, but real client code will get correct
                 * result after ContentService injects the mimetype.
                 *
                 * Note that we fail for mimetype == null in initFileContentReader()
                 */
                exists = mimetype == null || DummyFallbackContentStore.this.tempFileCache.containsKey(mimetype)
                        || DummyFallbackContentStore.this.isDummyFileAvailable(mimetype)
                        || DummyFallbackContentStore.this.isTransformerDebugFileAvailable(mimetype)
                        || DummyFallbackContentStore.this.isLazilyTransformedDummyFileAvailable(mimetype);
            }
            return exists;
        }

        /**
         *
         * {@inheritDoc}
         */
        @Override
        public long getLastModified()
        {
            return System.currentTimeMillis();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getContentUrl()
        {
            return this.contentURL;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getMimetype()
        {
            return this.mimetype;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void setMimetype(final String mimetype)
        {
            this.mimetype = mimetype;
            if (this.delegate != null)
            {
                this.delegate.setMimetype(mimetype);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getEncoding()
        {
            return this.encoding;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void setEncoding(final String encoding)
        {
            this.encoding = encoding;
            if (this.delegate != null)
            {
                this.delegate.setEncoding(encoding);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Locale getLocale()
        {
            return this.locale;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void setLocale(final Locale locale)
        {
            this.locale = locale;
            if (this.delegate != null)
            {
                this.delegate.setLocale(locale);
            }
        }

        /**
         *
         * {@inheritDoc}
         */
        @Override
        public ContentReader getReader()
        {
            final DummyFallbackContentReader reader = new DummyFallbackContentReader(this.getContentUrl());
            reader.setMimetype(this.getMimetype());
            reader.setEncoding(this.getEncoding());
            reader.setLocale(this.getLocale());
            return reader;
        }

        /**
         *
         * {@inheritDoc}
         */
        @Override
        public String toString()
        {
            final String string = this.getClass().getSimpleName() + " - " + this.getContentUrl() + " - "
                    + String.valueOf(this.getMimetype());
            return string;
        }

        /**
         *
         * {@inheritDoc}
         */
        @Override
        protected ContentReader initDelegate()
        {
            final String mimetype = this.getMimetype();
            if (mimetype == null)
            {
                LOGGER.error("Failed to initialise delegate reader for {} since no mimetype has been set", this.getContentUrl());
                throw new ContentIOException("Cannot initialise backing content reader without mimetype");
            }

            File temporaryFile = DummyFallbackContentStore.this.tempFileCache.get(mimetype);
            // check if temporary file is invalid
            if (temporaryFile == null || (System.currentTimeMillis() - temporaryFile.lastModified()) > LONG_LIFE_DURATOIN)
            {
                final String extension = DummyFallbackContentStore.this.mimetypeService.getExtension(mimetype);
                if (DummyFallbackContentStore.this.isDummyFileAvailable(mimetype))
                {
                    LOGGER.debug("Initialising shared temporary file for {} from provided dummy file", mimetype);
                    temporaryFile = this.initTemporaryFileFromDummy(mimetype, extension);
                }
                else if (DummyFallbackContentStore.this.isTransformerDebugFileAvailable(mimetype))
                {
                    LOGGER.debug("Initialising shared temporary file for {} from TransformerDebug test file", mimetype);
                    temporaryFile = this.initTemporaryFileFromTransformerDebugDummy(mimetype, extension);
                }
                else if (DummyFallbackContentStore.this.isLazilyTransformedDummyFileAvailable(mimetype))
                {
                    LOGGER.debug("Initialising shared temporary file for {} via transformation from provided dummy file", mimetype);
                    temporaryFile = this.initTemporaryFileFromLazilyTransformedDummy(mimetype, extension);
                }
            }

            if (temporaryFile == null)
            {
                LOGGER.error("Failed to initialise shared temporary file for mimetype {}", mimetype);
                throw new ContentIOException("Failed to initialise backing content reader since no dummy content is available");
            }

            return new FileContentReaderImpl(temporaryFile);
        }

        protected File initTemporaryFileFromDummy(final String mimetype, final String extension)
        {
            final File temporaryFile;
            final Resource dummyFileResource = DummyFallbackContentStore.this.getDummyFileResource(mimetype);
            try (final InputStream inputStream = dummyFileResource.getInputStream())
            {
                final File longLifeTempDir = TempFileProvider.getLongLifeTempDir(DummyFallbackContentStore.class.getName());
                temporaryFile = new File(longLifeTempDir, UUID.randomUUID().toString() + "." + extension);
                try (final OutputStream outputStream = new FileOutputStream(temporaryFile))
                {
                    IOUtils.copy(inputStream, outputStream);
                }
            }
            catch (final IOException ioex)
            {
                LOGGER.error("Failed to copy dummy file for mimetype {} from {} to shared temporary file", mimetype, dummyFileResource,
                        ioex);
                throw new ContentIOException("Failed to copy dummy file to shared temporary file", ioex);
            }
            DummyFallbackContentStore.this.tempFileCache.put(mimetype, temporaryFile);
            return temporaryFile;
        }

        protected File initTemporaryFileFromTransformerDebugDummy(final String mimetype, final String extension)
        {
            final File temporaryFile;
            final URL transformerDebugFileURL = DummyFallbackContentStore.this.getTransformerDebugFileURL(mimetype);
            try (InputStream inputStream = transformerDebugFileURL.openStream())
            {
                final File longLifeTempDir = TempFileProvider.getLongLifeTempDir(DummyFallbackContentStore.class.getName());
                temporaryFile = new File(longLifeTempDir, UUID.randomUUID().toString() + "." + extension);
                try (final OutputStream outputStream = new FileOutputStream(temporaryFile))
                {
                    IOUtils.copy(inputStream, outputStream);
                }
            }
            catch (final IOException ioex)
            {
                LOGGER.error("Failed to copy dummy file for mimetype {} from {} to shared temporary file", mimetype,
                        transformerDebugFileURL, ioex);
                throw new ContentIOException("Failed to copy dummy file to shared temporary file", ioex);
            }
            DummyFallbackContentStore.this.tempFileCache.put(mimetype, temporaryFile);
            return temporaryFile;
        }

        protected File initTemporaryFileFromLazilyTransformedDummy(final String mimetype, final String extension)
        {
            final File longLifeTempDir = TempFileProvider.getLongLifeTempDir(DummyFallbackContentStore.class.getName());
            File temporaryFile = new File(longLifeTempDir, UUID.randomUUID().toString() + "." + extension);

            for (final String sourceMimetype : DummyFallbackContentStore.this.transformationCandidateSourceMimetypes)
            {
                if (DummyFallbackContentStore.this.isDummyFileAvailable(sourceMimetype))
                {
                    final Resource dummyFileResource = DummyFallbackContentStore.this.getDummyFileResource(sourceMimetype);
                    final File sourceDummyTempFile = TempFileProvider.createTempFile(DummyFallbackContentStore.class.getName(),
                            DummyFallbackContentStore.this.mimetypeService.getExtension(sourceMimetype));
                    try
                    {
                        try (final InputStream inputStream = dummyFileResource.getInputStream())
                        {
                            try (final OutputStream outputStream = new FileOutputStream(sourceDummyTempFile))
                            {
                                IOUtils.copy(inputStream, outputStream);
                            }
                        }
                        catch (final IOException ioex)
                        {
                            LOGGER.error("Failed to copy source dummy file for mimetype {} to shared temporary file", sourceMimetype, ioex);
                            throw new ContentIOException("Failed to copy source dummy file to shared temporary file", ioex);
                        }

                        final ContentReader reader = new FileContentReaderImpl(sourceDummyTempFile);
                        final ContentWriter writer = new FileContentWriterImpl(temporaryFile);
                        final TransformationOptions options = new TransformationOptions();
                        if (DummyFallbackContentStore.this.contentService.isTransformable(reader, writer, options))
                        {
                            DummyFallbackContentStore.this.contentService.transform(reader, writer, options);
                        }
                        else
                        {
                            LOGGER.debug("Source mimetype {} cannot be transformed to {}", sourceMimetype, mimetype);
                        }

                        if (writer.getSize() > 0)
                        {
                            LOGGER.debug("Lazily transformed dummy for mimetype {} to requested mimetype {}", sourceMimetype,
                                    mimetype);
                            break;
                        }
                        else
                        {
                            LOGGER.debug("Transformation from mimetype {} to {} resulted in empty content ile", sourceMimetype, mimetype);
                            if (temporaryFile.exists() && !temporaryFile.delete())
                            {
                                temporaryFile.deleteOnExit();
                                temporaryFile = new File(longLifeTempDir, UUID.randomUUID().toString() + "." + extension);
                            }
                        }
                    }
                    finally
                    {
                        if (!sourceDummyTempFile.delete())
                        {
                            sourceDummyTempFile.deleteOnExit();
                        }
                    }
                }
                else
                {
                    LOGGER.debug("No dummy file provided for source mimetype {}", mimetype);
                }
            }

            if (temporaryFile.exists())
            {
                DummyFallbackContentStore.this.tempFileCache.put(mimetype, temporaryFile);
            }
            else
            {
                LOGGER.warn("Failed to initialise shared temporary file for dummy of mimetype {}", mimetype);
                temporaryFile = null;
            }

            return temporaryFile;
        }

    }
}
