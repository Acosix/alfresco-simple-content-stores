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
package de.acosix.alfresco.simplecontentstores.repo;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

import org.alfresco.repo.content.ContentContext;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.namespace.DynamicNamespacePrefixResolver;
import org.alfresco.service.namespace.NamespaceService;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.easymock.EasyMock;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.thedeanda.lorem.Lorem;
import com.thedeanda.lorem.LoremIpsum;

import de.acosix.alfresco.simplecontentstores.repo.store.context.ContentStoreContext;
import de.acosix.alfresco.simplecontentstores.repo.store.context.ContentStoreContext.ContentStoreOperation;
import de.acosix.alfresco.simplecontentstores.repo.store.facade.CompressingContentStore;
import de.acosix.alfresco.simplecontentstores.repo.store.file.FileContentStore;

/**
 *
 * @author Axel Faust
 */
public class CompressingContentStoreTest
{

    private static final CompressorStreamFactory COMPRESSOR_STREAM_FACTORY = new CompressorStreamFactory();

    private static DynamicNamespacePrefixResolver PREFIX_RESOLVER;

    private static File backingStoreFolder;

    private static File temporaryStoreFolder;

    @BeforeClass
    public static void staticSetup() throws IOException
    {
        if (PREFIX_RESOLVER == null)
        {
            PREFIX_RESOLVER = new DynamicNamespacePrefixResolver();
            PREFIX_RESOLVER.registerNamespace(NamespaceService.SYSTEM_MODEL_PREFIX, NamespaceService.SYSTEM_MODEL_1_0_URI);
            PREFIX_RESOLVER.registerNamespace(NamespaceService.CONTENT_MODEL_PREFIX, NamespaceService.CONTENT_MODEL_1_0_URI);
        }

        backingStoreFolder = TestUtilities.createFolder();
        temporaryStoreFolder = TestUtilities.createFolder();
    }

    @AfterClass
    public static void staticTearDown()
    {
        TestUtilities.delete(backingStoreFolder);
        TestUtilities.delete(temporaryStoreFolder);
    }

    @Test
    public void simpleUnconfiguredDefaultCompression() throws Exception
    {
        final DictionaryService dictionaryService = EasyMock.mock(DictionaryService.class);

        final CompressingContentStore compressingContentStore = new CompressingContentStore();
        compressingContentStore.setNamespaceService(PREFIX_RESOLVER);
        compressingContentStore.setDictionaryService(dictionaryService);

        final FileContentStore fileContentStore = new FileContentStore();
        fileContentStore.setRootDirectory(backingStoreFolder.getAbsolutePath());
        fileContentStore.setProtocol("store");
        compressingContentStore.setBackingStore(fileContentStore);

        final FileContentStore temporaryContentStore = new FileContentStore();
        temporaryContentStore.setRootDirectory(temporaryStoreFolder.getAbsolutePath());
        temporaryContentStore.setProtocol("store");
        compressingContentStore.setTemporaryStore(temporaryContentStore);

        fileContentStore.afterPropertiesSet();
        temporaryContentStore.afterPropertiesSet();
        compressingContentStore.afterPropertiesSet();

        testCompressableMimetype(compressingContentStore, fileContentStore, MimetypeMap.MIMETYPE_TEXT_PLAIN, CompressorStreamFactory.GZIP);
    }

    @Test
    public void mimetypeRestrictedCompression() throws Exception
    {
        final DictionaryService dictionaryService = EasyMock.mock(DictionaryService.class);

        final CompressingContentStore compressingContentStore = new CompressingContentStore();
        compressingContentStore.setNamespaceService(PREFIX_RESOLVER);
        compressingContentStore.setDictionaryService(dictionaryService);
        compressingContentStore.setMimetypesToCompress(Arrays.asList(MimetypeMap.MIMETYPE_TEXT_PLAIN, MimetypeMap.MIMETYPE_XML));

        final FileContentStore fileContentStore = new FileContentStore();
        fileContentStore.setRootDirectory(backingStoreFolder.getAbsolutePath());
        fileContentStore.setProtocol("store");
        compressingContentStore.setBackingStore(fileContentStore);

        final FileContentStore temporaryContentStore = new FileContentStore();
        temporaryContentStore.setRootDirectory(temporaryStoreFolder.getAbsolutePath());
        temporaryContentStore.setProtocol("store");
        compressingContentStore.setTemporaryStore(temporaryContentStore);

        fileContentStore.afterPropertiesSet();
        temporaryContentStore.afterPropertiesSet();
        compressingContentStore.afterPropertiesSet();

        testCompressableMimetype(compressingContentStore, fileContentStore, MimetypeMap.MIMETYPE_TEXT_PLAIN, CompressorStreamFactory.GZIP);
        testCompressableMimetype(compressingContentStore, fileContentStore, MimetypeMap.MIMETYPE_XML, CompressorStreamFactory.GZIP);
        testUncompressableMimetype(compressingContentStore, fileContentStore, MimetypeMap.MIMETYPE_PDF);
    }

    @Test
    public void customCompression() throws Exception
    {
        final DictionaryService dictionaryService = EasyMock.mock(DictionaryService.class);

        final CompressingContentStore compressingContentStore = new CompressingContentStore();
        compressingContentStore.setNamespaceService(PREFIX_RESOLVER);
        compressingContentStore.setDictionaryService(dictionaryService);
        compressingContentStore.setCompressionType(CompressorStreamFactory.BZIP2);

        final FileContentStore fileContentStore = new FileContentStore();
        fileContentStore.setRootDirectory(backingStoreFolder.getAbsolutePath());
        fileContentStore.setProtocol("store");
        compressingContentStore.setBackingStore(fileContentStore);

        final FileContentStore temporaryContentStore = new FileContentStore();
        temporaryContentStore.setRootDirectory(temporaryStoreFolder.getAbsolutePath());
        temporaryContentStore.setProtocol("store");
        compressingContentStore.setTemporaryStore(temporaryContentStore);

        fileContentStore.afterPropertiesSet();
        temporaryContentStore.afterPropertiesSet();
        compressingContentStore.afterPropertiesSet();

        testCompressableMimetype(compressingContentStore, fileContentStore, MimetypeMap.MIMETYPE_TEXT_PLAIN, CompressorStreamFactory.BZIP2);
    }

    private static void testCompressableMimetype(final CompressingContentStore compressingContentStore,
            final FileContentStore fileContentStore, final String mimetype, final String compression) throws Exception
    {
        ContentStoreContext.executeInNewContext(new ContentStoreOperation<Object>()
        {

            /**
             *
             * {@inheritDoc}
             */
            @Override
            public Object execute()
            {
                final ContentWriter writer = compressingContentStore.getWriter(new ContentContext(null, null));
                final String testText = CompressingContentStoreTest.generateCopmressableText();
                writer.setMimetype(mimetype);
                writer.setEncoding(StandardCharsets.UTF_8.name());
                writer.setLocale(Locale.ENGLISH);
                writer.putContent(testText);

                final String contentUrl = writer.getContentUrl();
                Assert.assertNotNull("Content URL was not set after writing content", contentUrl);

                final ContentReader properReader = compressingContentStore.getReader(contentUrl);
                Assert.assertTrue("Reader was not returned for freshly written content", properReader != null);
                Assert.assertTrue("Reader does not refer to existing file for freshly written content", properReader.exists());

                // reader does not know about mimetype (provided via persisted ContentData at server runtime)
                properReader.setMimetype(mimetype);

                final String readText = properReader.getContentString();
                Assert.assertEquals("Read content does not match written test content", testText, readText);

                ContentReader backingReader = fileContentStore.getReader(contentUrl);
                Assert.assertTrue("Backing reader was not returned for freshly written content", backingReader != null);
                Assert.assertTrue("Backing reader does not refer to existing file for freshly written content", backingReader.exists());

                backingReader.setMimetype(mimetype);

                // can't test for size as this would (at server runtime) be handled via persisted ContentData, not actual file size on disk
                final String backingText = backingReader.getContentString();
                Assert.assertNotEquals("Backing reader did not return unreadable (compressed) content", testText, backingText);

                backingReader = fileContentStore.getReader(contentUrl);
                backingReader.setMimetype(mimetype);
                try
                {
                    final CompressorInputStream inputStream = COMPRESSOR_STREAM_FACTORY.createCompressorInputStream(compression,
                            backingReader.getContentInputStream());
                    final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8.name()));
                    final StringBuilder sb = new StringBuilder();
                    final char[] buf = new char[1024];
                    int read = 0;
                    while (read != -1)
                    {
                        sb.append(buf, 0, read);
                        read = reader.read(buf);
                    }

                    Assert.assertEquals("Decompressed content does not match test content", testText, sb.toString());
                }
                catch (final Exception ex)
                {
                    Assert.fail(ex.getMessage());
                }

                return null;
            }
        });
    }

    private static void testUncompressableMimetype(final CompressingContentStore compressingContentStore,
            final FileContentStore fileContentStore, final String mimetype) throws Exception
    {
        ContentStoreContext.executeInNewContext(new ContentStoreOperation<Object>()
        {

            /**
             *
             * {@inheritDoc}
             */
            @Override
            public Object execute()
            {
                final ContentWriter writer = compressingContentStore.getWriter(new ContentContext(null, null));
                final String testText = CompressingContentStoreTest.generateCopmressableText();
                writer.setMimetype(mimetype);
                writer.setEncoding(StandardCharsets.UTF_8.name());
                writer.setLocale(Locale.ENGLISH);
                writer.putContent(testText);

                final String contentUrl = writer.getContentUrl();
                Assert.assertNotNull("Content URL was not set after writing content", contentUrl);

                final ContentReader properReader = compressingContentStore.getReader(contentUrl);
                Assert.assertTrue("Reader was not returned for freshly written content", properReader != null);
                Assert.assertTrue("Reader does not refer to existing file for freshly written content", properReader.exists());

                // reader does not know about mimetype (provided via persisted ContentData at server runtime)
                properReader.setMimetype(mimetype);

                final String readText = properReader.getContentString();
                Assert.assertEquals("Read content does not match written test content", testText, readText);

                final ContentReader backingReader = fileContentStore.getReader(contentUrl);
                Assert.assertTrue("Backing reader was not returned for freshly written content", backingReader != null);
                Assert.assertTrue("Backing reader does not refer to existing file for freshly written content", backingReader.exists());

                backingReader.setMimetype(mimetype);

                // can't test for size as this would (at server runtime) be handled via persisted ContentData, not actual file size on disk
                final String backingText = backingReader.getContentString();
                Assert.assertEquals("Backing reader did not return unaltered content for uncompressable mimetype", testText, backingText);

                return null;
            }
        });
    }

    private static String generateCopmressableText()
    {
        // use class name hash code as seed for PNG for reproducible / comparable content
        final Lorem lorem = new LoremIpsum(Long.valueOf(CompressingContentStoreTest.class.getName().hashCode()));
        final String text = lorem.getParagraphs(5, 25);
        return text;
    }
}
