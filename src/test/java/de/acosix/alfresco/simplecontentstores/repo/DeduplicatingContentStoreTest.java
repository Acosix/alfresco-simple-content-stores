/*
 * Copyright 2017, 2018 Acosix GmbH
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

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;

import org.alfresco.repo.content.ContentContext;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.namespace.DynamicNamespacePrefixResolver;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.util.GUID;
import org.easymock.EasyMock;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.thedeanda.lorem.Lorem;
import com.thedeanda.lorem.LoremIpsum;

import de.acosix.alfresco.simplecontentstores.repo.store.context.ContentStoreContext;
import de.acosix.alfresco.simplecontentstores.repo.store.facade.DeduplicatingContentStore;
import de.acosix.alfresco.simplecontentstores.repo.store.file.FileContentStore;

/**
 *
 * @author Axel Faust, <a href="http://acosix.de">Acosix GmbH</a>
 */
public class DeduplicatingContentStoreTest
{

    private static final SecureRandom SEED_PNG;
    static
    {
        try
        {
            SEED_PNG = new SecureRandom(DeduplicatingContentStoreTest.class.getName().getBytes(StandardCharsets.UTF_8.name()));
        }
        catch (final UnsupportedEncodingException ex)
        {
            throw new RuntimeException("Java does not support UTF-8 anymore, so run for your lives...", ex);
        }
    }

    private static DynamicNamespacePrefixResolver PREFIX_RESOLVER;

    private static File baseFolder;

    private static File backingStoreFolder;

    private static File temporaryStoreFolder;

    @BeforeClass
    public static void staticSetup()
    {
        if (PREFIX_RESOLVER == null)
        {
            PREFIX_RESOLVER = new DynamicNamespacePrefixResolver();
            PREFIX_RESOLVER.registerNamespace(NamespaceService.SYSTEM_MODEL_PREFIX, NamespaceService.SYSTEM_MODEL_1_0_URI);
            PREFIX_RESOLVER.registerNamespace(NamespaceService.CONTENT_MODEL_PREFIX, NamespaceService.CONTENT_MODEL_1_0_URI);
        }

        // use GUID to avoid accidental reuse of folder from previous run
        baseFolder = new File(System.getProperty("java.io.tmpdir") + "/" + GUID.generate());
        baseFolder.mkdirs();

        backingStoreFolder = new File(baseFolder, "backingStore");
        backingStoreFolder.mkdirs();

        temporaryStoreFolder = new File(baseFolder, "temporaryStore");
        temporaryStoreFolder.mkdirs();
    }

    @AfterClass
    public static void staticTearDown()
    {
        if (!backingStoreFolder.delete())
        {
            backingStoreFolder.deleteOnExit();
        }
        if (!temporaryStoreFolder.delete())
        {
            temporaryStoreFolder.deleteOnExit();
        }
        if (!baseFolder.delete())
        {
            baseFolder.deleteOnExit();
        }
    }

    @Test
    public void unconfiguredDeduplication() throws Exception
    {
        final DictionaryService dictionaryService = EasyMock.mock(DictionaryService.class);

        final DeduplicatingContentStore deduplicatingContentStore = new DeduplicatingContentStore();
        deduplicatingContentStore.setNamespaceService(PREFIX_RESOLVER);
        deduplicatingContentStore.setDictionaryService(dictionaryService);

        final FileContentStore fileContentStore = new FileContentStore();
        fileContentStore.setRootDirectory(backingStoreFolder.getAbsolutePath());
        fileContentStore.setProtocol("store");
        deduplicatingContentStore.setBackingStore(fileContentStore);

        final FileContentStore temporaryContentStore = new FileContentStore();
        temporaryContentStore.setRootDirectory(temporaryStoreFolder.getAbsolutePath());
        temporaryContentStore.setProtocol("store");
        deduplicatingContentStore.setTemporaryStore(temporaryContentStore);

        fileContentStore.afterPropertiesSet();
        temporaryContentStore.afterPropertiesSet();
        deduplicatingContentStore.afterPropertiesSet();

        final String commonText = generateText(SEED_PNG.nextLong());
        final String differentText = generateText(SEED_PNG.nextLong());
        final ContentWriter firstWriter = testIndividualWriteAndRead(deduplicatingContentStore, commonText);
        final ContentWriter secondWriter = testIndividualWriteAndRead(deduplicatingContentStore, commonText);
        final ContentWriter thirdWriter = testIndividualWriteAndRead(deduplicatingContentStore, differentText);

        Assert.assertEquals("Content URL of second writer does not match previous writer of identical content", firstWriter.getContentUrl(),
                secondWriter.getContentUrl());
        Assert.assertNotEquals("Content URL of third writer matches previous writer of non-identical content", firstWriter.getContentUrl(),
                thirdWriter.getContentUrl());

        Assert.assertTrue("Content URL of first writer does not contain expected path segments of 3x 2 bytes and SHA-512 hash",
                firstWriter.getContentUrl().matches("^[^:]++://([a-fA-F0-9]{4}/){3}[a-fA-F0-9]{128}\\.bin$"));
        Assert.assertTrue("Content URL of third writer does not contain expected path segments of 3x 2 bytes and SHA-512 hash",
                thirdWriter.getContentUrl().matches("^[^:]++://([a-fA-F0-9]{4}/){3}[a-fA-F0-9]{128}\\.bin$"));
    }

    @Test
    public void noPathPrefixDeduplication() throws Exception
    {
        final DictionaryService dictionaryService = EasyMock.mock(DictionaryService.class);

        final DeduplicatingContentStore deduplicatingContentStore = new DeduplicatingContentStore();
        deduplicatingContentStore.setNamespaceService(PREFIX_RESOLVER);
        deduplicatingContentStore.setDictionaryService(dictionaryService);
        deduplicatingContentStore.setPathSegments(0);

        final FileContentStore fileContentStore = new FileContentStore();
        fileContentStore.setRootDirectory(backingStoreFolder.getAbsolutePath());
        fileContentStore.setProtocol("store");
        deduplicatingContentStore.setBackingStore(fileContentStore);

        final FileContentStore temporaryContentStore = new FileContentStore();
        temporaryContentStore.setRootDirectory(temporaryStoreFolder.getAbsolutePath());
        temporaryContentStore.setProtocol("store");
        deduplicatingContentStore.setTemporaryStore(temporaryContentStore);

        fileContentStore.afterPropertiesSet();
        temporaryContentStore.afterPropertiesSet();
        deduplicatingContentStore.afterPropertiesSet();

        final String commonText = generateText(SEED_PNG.nextLong());
        final ContentWriter writer = testIndividualWriteAndRead(deduplicatingContentStore, commonText);

        Assert.assertTrue("Content URL of writer does includes unwanted path elements",
                writer.getContentUrl().matches("^[^:]++://[a-fA-F0-9]{128}\\.bin$"));
    }

    @Test
    public void customPathPrefixDeduplication() throws Exception
    {
        final DictionaryService dictionaryService = EasyMock.mock(DictionaryService.class);

        final DeduplicatingContentStore deduplicatingContentStore = new DeduplicatingContentStore();
        deduplicatingContentStore.setNamespaceService(PREFIX_RESOLVER);
        deduplicatingContentStore.setDictionaryService(dictionaryService);
        deduplicatingContentStore.setPathSegments(5);
        deduplicatingContentStore.setBytesPerPathSegment(5);

        final FileContentStore fileContentStore = new FileContentStore();
        fileContentStore.setRootDirectory(backingStoreFolder.getAbsolutePath());
        fileContentStore.setProtocol("store");
        deduplicatingContentStore.setBackingStore(fileContentStore);

        final FileContentStore temporaryContentStore = new FileContentStore();
        temporaryContentStore.setRootDirectory(temporaryStoreFolder.getAbsolutePath());
        temporaryContentStore.setProtocol("store");
        deduplicatingContentStore.setTemporaryStore(temporaryContentStore);

        fileContentStore.afterPropertiesSet();
        temporaryContentStore.afterPropertiesSet();
        deduplicatingContentStore.afterPropertiesSet();

        final String commonText = generateText(SEED_PNG.nextLong());
        final ContentWriter writer = testIndividualWriteAndRead(deduplicatingContentStore, commonText);

        Assert.assertTrue("Content URL of writer does not contain expected path segments of 5x 5 bytes and SHA-512 hash",
                writer.getContentUrl().matches("^[^:]++://([a-fA-F0-9]{10}/){5}[a-fA-F0-9]{128}\\.bin$"));
    }

    @Test
    public void customDigestDeduplication() throws Exception
    {
        final DictionaryService dictionaryService = EasyMock.mock(DictionaryService.class);

        final DeduplicatingContentStore deduplicatingContentStore = new DeduplicatingContentStore();
        deduplicatingContentStore.setNamespaceService(PREFIX_RESOLVER);
        deduplicatingContentStore.setDictionaryService(dictionaryService);
        deduplicatingContentStore.setDigestAlgorithm("SHA-256");

        final FileContentStore fileContentStore = new FileContentStore();
        fileContentStore.setRootDirectory(backingStoreFolder.getAbsolutePath());
        fileContentStore.setProtocol("store");
        deduplicatingContentStore.setBackingStore(fileContentStore);

        final FileContentStore temporaryContentStore = new FileContentStore();
        temporaryContentStore.setRootDirectory(temporaryStoreFolder.getAbsolutePath());
        temporaryContentStore.setProtocol("store");
        deduplicatingContentStore.setTemporaryStore(temporaryContentStore);

        fileContentStore.afterPropertiesSet();
        temporaryContentStore.afterPropertiesSet();
        deduplicatingContentStore.afterPropertiesSet();

        final String commonText = generateText(SEED_PNG.nextLong());
        final ContentWriter writer = testIndividualWriteAndRead(deduplicatingContentStore, commonText);

        Assert.assertTrue("Content URL of writer does not contain expected path segments of 3x 2 bytes and SHA-256 hash",
                writer.getContentUrl().matches("^[^:]++://([a-fA-F0-9]{4}/){3}[a-fA-F0-9]{64}\\.bin$"));
    }

    private static ContentWriter testIndividualWriteAndRead(final DeduplicatingContentStore deduplicatingContentStore,
            final String testText)
    {
        return ContentStoreContext.executeInNewContext(() -> {
            final ContentWriter writer = deduplicatingContentStore.getWriter(new ContentContext(null, null));
            writer.setMimetype(MimetypeMap.MIMETYPE_TEXT_PLAIN);
            writer.setEncoding(StandardCharsets.UTF_8.name());
            writer.setLocale(Locale.ENGLISH);
            writer.putContent(testText);

            final String contentUrl = writer.getContentUrl();
            Assert.assertNotNull("Content URL was not set after writing content", contentUrl);

            final ContentReader properReader = deduplicatingContentStore.getReader(contentUrl);
            Assert.assertTrue("Reader was not returned for freshly written content", properReader != null);
            Assert.assertTrue("Reader does not refer to existing file for freshly written content", properReader.exists());

            // reader does not know about mimetype (provided via persisted ContentData at server runtime)
            properReader.setMimetype(MimetypeMap.MIMETYPE_TEXT_PLAIN);

            final String readText = properReader.getContentString();
            Assert.assertEquals("Read content does not match written test content", testText, readText);

            return writer;
        });
    }

    private static String generateText(final long seed)
    {
        final Lorem lorem = new LoremIpsum(Long.valueOf(seed));
        final String text = lorem.getParagraphs(5, 25);
        return text;
    }
}
