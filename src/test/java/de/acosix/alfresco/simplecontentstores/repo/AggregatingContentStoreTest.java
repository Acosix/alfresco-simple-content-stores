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
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;

import org.alfresco.repo.content.ContentContext;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.thedeanda.lorem.Lorem;
import com.thedeanda.lorem.LoremIpsum;

import de.acosix.alfresco.simplecontentstores.repo.store.StoreConstants;
import de.acosix.alfresco.simplecontentstores.repo.store.combination.AggregatingContentStore;
import de.acosix.alfresco.simplecontentstores.repo.store.context.ContentStoreContext;
import de.acosix.alfresco.simplecontentstores.repo.store.context.ContentStoreContext.ContentStoreOperation;
import de.acosix.alfresco.simplecontentstores.repo.store.file.FileContentStore;

/**
 *
 * @author Axel Faust
 */
public class AggregatingContentStoreTest
{

    private static final String STORE_1_PROTOCOL = "store1";

    private static final String STORE_2_PROTOCOL = "store2";

    private static final String STORE_3_PROTOCOL = "store3";

    private static final SecureRandom SEED_PRNG;
    static
    {
        try
        {
            SEED_PRNG = new SecureRandom(AggregatingContentStoreTest.class.getName().getBytes(StandardCharsets.UTF_8.name()));
        }
        catch (final UnsupportedEncodingException ex)
        {
            throw new RuntimeException("Java does not support UTF-8 anymore, so run for your lives...", ex);
        }
    }

    private static File store1Folder;

    private static File store2Folder;

    private static File store3Folder;

    @BeforeClass
    public static void staticSetup() throws IOException
    {
        store1Folder = TestUtilities.createFolder();
        store2Folder = TestUtilities.createFolder();
        store3Folder = TestUtilities.createFolder();
    }

    @AfterClass
    public static void staticTearDown()
    {
        TestUtilities.delete(store1Folder);
        TestUtilities.delete(store2Folder);
        TestUtilities.delete(store3Folder);
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void primaryOnlyWrite() throws Exception
    {
        final AggregatingContentStore aggregatingContentStore = new AggregatingContentStore();

        final FileContentStore store1 = new FileContentStore();
        store1.setRootDirectory(store1Folder.getAbsolutePath());
        store1.setProtocol(STORE_1_PROTOCOL);
        aggregatingContentStore.setPrimaryStore(store1);

        final FileContentStore store2 = new FileContentStore();
        store2.setRootDirectory(store2Folder.getAbsolutePath());
        store2.setProtocol(STORE_2_PROTOCOL);

        final FileContentStore store3 = new FileContentStore();
        store3.setRootDirectory(store3Folder.getAbsolutePath());
        store3.setProtocol(STORE_3_PROTOCOL);

        aggregatingContentStore.setSecondaryStores(Arrays.<ContentStore> asList(store2, store3));

        store1.afterPropertiesSet();
        store2.afterPropertiesSet();
        store3.afterPropertiesSet();
        aggregatingContentStore.afterPropertiesSet();

        final String primaryText1 = generateText(SEED_PRNG.nextLong());
        final String primaryText2 = generateText(SEED_PRNG.nextLong());

        final ContentWriter primaryWriter1 = testIndividualWriteAndRead(aggregatingContentStore, primaryText1, STORE_1_PROTOCOL);

        final String contentUrl1 = primaryWriter1.getContentUrl();
        final String wildcardContentUrl = contentUrl1.replaceFirst(STORE_1_PROTOCOL, StoreConstants.WILDCARD_PROTOCOL);
        Assert.assertTrue("Aggregating content store did not write content to primary store", store1.exists(wildcardContentUrl));
        Assert.assertFalse("Aggregating content store wrote content to 1st secondcary store", store2.exists(wildcardContentUrl));
        Assert.assertFalse("Aggregating content store wrote content to 2nd secondcary store", store3.exists(wildcardContentUrl));

        final ContentReader reader1 = store1.getReader(wildcardContentUrl);
        Assert.assertTrue("Aggregating store did not return valid reader for content URL in primary store",
                reader1 != null && reader1.exists());
        Assert.assertEquals("Content retrieved from primary store does not match content written via aggregating store", primaryText1,
                reader1.getContentString());

        store1.setReadOnly(true);

        this.thrown.expect(UnsupportedOperationException.class);
        testIndividualWriteAndRead(aggregatingContentStore, primaryText2, STORE_1_PROTOCOL);
    }

    @Test
    public void primaryOnlyDeletion() throws Exception
    {
        final AggregatingContentStore aggregatingContentStore = new AggregatingContentStore();
        aggregatingContentStore.setDeleteContentFromSecondaryStores(false);

        // all stores need to use identical protocol in content URLs
        final FileContentStore store1 = new FileContentStore();
        store1.setRootDirectory(store1Folder.getAbsolutePath());
        store1.setProtocol(STORE_1_PROTOCOL);
        aggregatingContentStore.setPrimaryStore(store1);

        final FileContentStore store2 = new FileContentStore();
        store2.setRootDirectory(store2Folder.getAbsolutePath());
        store2.setProtocol(STORE_1_PROTOCOL);

        final FileContentStore store3 = new FileContentStore();
        store3.setRootDirectory(store3Folder.getAbsolutePath());
        store3.setProtocol(STORE_1_PROTOCOL);

        aggregatingContentStore.setSecondaryStores(Arrays.<ContentStore> asList(store2, store3));

        store1.afterPropertiesSet();
        store2.afterPropertiesSet();
        store3.afterPropertiesSet();
        aggregatingContentStore.afterPropertiesSet();

        final String text = generateText(SEED_PRNG.nextLong());

        final ContentWriter primaryWriter = testIndividualWriteAndRead(aggregatingContentStore, text, STORE_1_PROTOCOL);
        final String contentUrl = primaryWriter.getContentUrl();

        // copy into secondary stores
        final String wildcardContentUrl = contentUrl.replaceFirst(STORE_1_PROTOCOL, StoreConstants.WILDCARD_PROTOCOL);
        final ContentWriter store2Writer = store2.getWriter(new ContentContext(null, wildcardContentUrl));
        store2Writer.putContent(primaryWriter.getReader());

        final ContentWriter store3Writer = store3.getWriter(new ContentContext(null, wildcardContentUrl));
        store3Writer.putContent(primaryWriter.getReader());

        final boolean deleted = aggregatingContentStore.delete(contentUrl);
        Assert.assertTrue("Aggregating content store did not report content as deleted", deleted);
        Assert.assertFalse("Primary store still contains deleted content", store1.exists(contentUrl));
        Assert.assertTrue("1st secondary store does not still contain content despite deletion not allowed for secondary stores",
                store2.exists(store2Writer.getContentUrl()));
        Assert.assertTrue("2nd secondary store does not still contain content despite deletion not allowed for secondary stores",
                store3.exists(store3Writer.getContentUrl()));
    }

    @Test
    public void defaultSecondaryStoreDeletion() throws Exception
    {
        final AggregatingContentStore aggregatingContentStore = new AggregatingContentStore();

        // all stores need to use identical protocol in content URLs
        final FileContentStore store1 = new FileContentStore();
        store1.setRootDirectory(store1Folder.getAbsolutePath());
        store1.setProtocol(STORE_1_PROTOCOL);
        aggregatingContentStore.setPrimaryStore(store1);

        final FileContentStore store2 = new FileContentStore();
        store2.setRootDirectory(store2Folder.getAbsolutePath());
        store2.setProtocol(STORE_1_PROTOCOL);

        final FileContentStore store3 = new FileContentStore();
        store3.setRootDirectory(store3Folder.getAbsolutePath());
        store3.setProtocol(STORE_1_PROTOCOL);

        aggregatingContentStore.setSecondaryStores(Arrays.<ContentStore> asList(store2, store3));

        store1.afterPropertiesSet();
        store2.afterPropertiesSet();
        store3.afterPropertiesSet();
        aggregatingContentStore.afterPropertiesSet();

        final String text = generateText(SEED_PRNG.nextLong());

        final ContentWriter primaryWriter = testIndividualWriteAndRead(aggregatingContentStore, text, STORE_1_PROTOCOL);
        final String contentUrl = primaryWriter.getContentUrl();

        // copy into secondary stores
        final String wildcardContentUrl = contentUrl.replaceFirst(STORE_1_PROTOCOL, StoreConstants.WILDCARD_PROTOCOL);
        final ContentWriter store2Writer = store2.getWriter(new ContentContext(null, wildcardContentUrl));
        store2Writer.putContent(primaryWriter.getReader());

        final ContentWriter store3Writer = store3.getWriter(new ContentContext(null, wildcardContentUrl));
        store3Writer.putContent(primaryWriter.getReader());

        final boolean deleted = aggregatingContentStore.delete(contentUrl);
        Assert.assertTrue("Aggregating content store did not report content as deleted", deleted);
        Assert.assertFalse("Primary store still contains deleted content", store1.exists(contentUrl));
        Assert.assertFalse("1st secondary store still contains deleted content", store2.exists(store2Writer.getContentUrl()));
        Assert.assertFalse("2nd secondary store still contains deleted content", store3.exists(store3Writer.getContentUrl()));
    }

    @Test
    public void defaultSecondaryStoreDeletionWithPartialReadOnlyStores() throws Exception
    {
        final AggregatingContentStore aggregatingContentStore = new AggregatingContentStore();

        // all stores need to use identical protocol in content URLs
        final FileContentStore store1 = new FileContentStore();
        store1.setRootDirectory(store1Folder.getAbsolutePath());
        store1.setProtocol(STORE_1_PROTOCOL);
        aggregatingContentStore.setPrimaryStore(store1);

        final FileContentStore store2 = new FileContentStore();
        store2.setRootDirectory(store2Folder.getAbsolutePath());
        store2.setProtocol(STORE_1_PROTOCOL);

        final FileContentStore store3 = new FileContentStore();
        store3.setRootDirectory(store3Folder.getAbsolutePath());
        store3.setProtocol(STORE_1_PROTOCOL);

        aggregatingContentStore.setSecondaryStores(Arrays.<ContentStore> asList(store2, store3));

        store1.afterPropertiesSet();
        store2.afterPropertiesSet();
        store3.afterPropertiesSet();
        aggregatingContentStore.afterPropertiesSet();

        final String text = generateText(SEED_PRNG.nextLong());

        final ContentWriter primaryWriter = testIndividualWriteAndRead(aggregatingContentStore, text, STORE_1_PROTOCOL);
        final String contentUrl = primaryWriter.getContentUrl();

        // copy into secondary stores
        final String wildcardContentUrl = contentUrl.replaceFirst(STORE_1_PROTOCOL, StoreConstants.WILDCARD_PROTOCOL);
        final ContentWriter store2Writer = store2.getWriter(new ContentContext(null, wildcardContentUrl));
        store2Writer.putContent(primaryWriter.getReader());

        final ContentWriter store3Writer = store3.getWriter(new ContentContext(null, wildcardContentUrl));
        store3Writer.putContent(primaryWriter.getReader());

        // mark 2nd secondary store as read-only
        store3.setReadOnly(true);

        final boolean deleted = aggregatingContentStore.delete(contentUrl);
        Assert.assertTrue("Aggregating content store did not report content as deleted", deleted);
        Assert.assertFalse("Primary store still contains deleted content", store1.exists(contentUrl));
        Assert.assertFalse("1st secondary store still contains deleted content", store2.exists(store2Writer.getContentUrl()));
        Assert.assertTrue("2nd secondary store does not still contain content despite store being read-only",
                store3.exists(store3Writer.getContentUrl()));
    }

    @Test
    public void defaultSecondaryStoreDeletionWithPartiallyDifferentProtocol() throws Exception
    {
        final AggregatingContentStore aggregatingContentStore = new AggregatingContentStore();

        // all stores need to use identical protocol in content URLs
        final FileContentStore store1 = new FileContentStore();
        store1.setRootDirectory(store1Folder.getAbsolutePath());
        store1.setProtocol(STORE_1_PROTOCOL);
        aggregatingContentStore.setPrimaryStore(store1);

        final FileContentStore store2 = new FileContentStore();
        store2.setRootDirectory(store2Folder.getAbsolutePath());
        store2.setProtocol(STORE_1_PROTOCOL);

        final FileContentStore store3 = new FileContentStore();
        store3.setRootDirectory(store3Folder.getAbsolutePath());
        store3.setProtocol(STORE_3_PROTOCOL);

        aggregatingContentStore.setSecondaryStores(Arrays.<ContentStore> asList(store2, store3));

        store1.afterPropertiesSet();
        store2.afterPropertiesSet();
        store3.afterPropertiesSet();
        aggregatingContentStore.afterPropertiesSet();

        final String text = generateText(SEED_PRNG.nextLong());

        final ContentWriter primaryWriter = testIndividualWriteAndRead(aggregatingContentStore, text, STORE_1_PROTOCOL);
        final String contentUrl = primaryWriter.getContentUrl();

        // copy into secondary stores
        final String wildcardContentUrl = contentUrl.replaceFirst(STORE_1_PROTOCOL, StoreConstants.WILDCARD_PROTOCOL);
        final ContentWriter store2Writer = store2.getWriter(new ContentContext(null, wildcardContentUrl));
        store2Writer.putContent(primaryWriter.getReader());

        final ContentWriter store3Writer = store3.getWriter(new ContentContext(null, wildcardContentUrl));
        store3Writer.putContent(primaryWriter.getReader());

        final boolean deleted = aggregatingContentStore.delete(contentUrl);
        Assert.assertTrue("Aggregating content store did not report content as deleted", deleted);
        Assert.assertFalse("Primary store still contains deleted content", store1.exists(contentUrl));
        Assert.assertFalse("1st secondary store still contains deleted content", store2.exists(contentUrl));
        Assert.assertTrue("2nd secondary store does not still contain content despite using separate store protocol",
                store3.exists(store3Writer.getContentUrl()));
    }

    @Test
    public void readAggregation() throws Exception
    {
        final AggregatingContentStore aggregatingContentStore = new AggregatingContentStore();

        final FileContentStore store1 = new FileContentStore();
        store1.setRootDirectory(store1Folder.getAbsolutePath());
        store1.setProtocol(STORE_1_PROTOCOL);
        aggregatingContentStore.setPrimaryStore(store1);

        final FileContentStore store2 = new FileContentStore();
        store2.setRootDirectory(store2Folder.getAbsolutePath());
        store2.setProtocol(STORE_2_PROTOCOL);

        final FileContentStore store3 = new FileContentStore();
        store3.setRootDirectory(store3Folder.getAbsolutePath());
        store3.setProtocol(STORE_3_PROTOCOL);

        aggregatingContentStore.setSecondaryStores(Arrays.<ContentStore> asList(store2, store3));

        store1.afterPropertiesSet();
        store2.afterPropertiesSet();
        store3.afterPropertiesSet();
        aggregatingContentStore.afterPropertiesSet();

        final String textStore1 = generateText(SEED_PRNG.nextLong());
        final String textStore2 = generateText(SEED_PRNG.nextLong());
        final String textStore3 = generateText(SEED_PRNG.nextLong());

        {
            final ContentWriter store1Writer = testIndividualWriteAndRead(store1, textStore1, STORE_1_PROTOCOL);

            final String contentUrl1 = store1Writer.getContentUrl();
            Assert.assertTrue("Aggregating store does not support URL of primary store",
                    aggregatingContentStore.isContentUrlSupported(contentUrl1));
            Assert.assertTrue("Aggregating store claims content URL of primary store does not exist",
                    aggregatingContentStore.exists(contentUrl1));

            final ContentReader reader1 = aggregatingContentStore.getReader(contentUrl1);
            Assert.assertTrue("Aggregating store did not return valid reader for content URL in primary store",
                    reader1 != null && reader1.exists());
            Assert.assertEquals("Content retrieved via aggregating store does not match content in primary store", textStore1,
                    reader1.getContentString());
        }

        {
            final ContentWriter store2Writer = testIndividualWriteAndRead(store2, textStore2, STORE_2_PROTOCOL);

            final String contentUrl2 = store2Writer.getContentUrl();
            Assert.assertTrue("Aggregating store does not support URL of 1st secondary store",
                    aggregatingContentStore.isContentUrlSupported(contentUrl2));
            Assert.assertTrue("Aggregating store claims content URL of 1st secondary store does not exist",
                    aggregatingContentStore.exists(contentUrl2));

            final ContentReader reader2 = aggregatingContentStore.getReader(contentUrl2);
            Assert.assertTrue("Aggregating store did not return valid reader for content URL in 1st secondary store",
                    reader2 != null && reader2.exists());
            Assert.assertEquals("Content retrieved via aggregating store does not match content in 1st secondary store", textStore2,
                    reader2.getContentString());
        }

        {
            final ContentWriter store3Writer = testIndividualWriteAndRead(store3, textStore3, STORE_3_PROTOCOL);

            final String contentUrl3 = store3Writer.getContentUrl();
            Assert.assertTrue("Aggregating store does not support URL of 2nd secondary store",
                    aggregatingContentStore.isContentUrlSupported(contentUrl3));
            Assert.assertTrue("Aggregating store claims content URL of 2nd secondary store does not exist",
                    aggregatingContentStore.exists(contentUrl3));

            final ContentReader reader3 = aggregatingContentStore.getReader(contentUrl3);
            Assert.assertTrue("Aggregating store did not return valid reader for content URL in 2nd secondary store",
                    reader3 != null && reader3.exists());
            Assert.assertEquals("Content retrieved via aggregating store does not match content in 2nd secondary store", textStore3,
                    reader3.getContentString());
        }
    }

    private static ContentWriter testIndividualWriteAndRead(final ContentStore contentStore, final String testText,
            final String expectedProtocol)
    {
        return ContentStoreContext.executeInNewContext(new ContentStoreOperation<ContentWriter>()
        {

            /**
             *
             * {@inheritDoc}
             */
            @Override
            public ContentWriter execute()
            {
                final ContentWriter writer = contentStore.getWriter(new ContentContext(null, null));
                writer.setMimetype(MimetypeMap.MIMETYPE_TEXT_PLAIN);
                writer.setEncoding(StandardCharsets.UTF_8.name());
                writer.setLocale(Locale.ENGLISH);
                writer.putContent(testText);

                final String contentUrl = writer.getContentUrl();
                Assert.assertNotNull("Content URL was not set after writing content", contentUrl);
                Assert.assertTrue("Content URL does not start with the configured protocol",
                        contentUrl.startsWith(expectedProtocol + ContentStore.PROTOCOL_DELIMITER));

                Assert.assertTrue("Store does not report content URL to exist after writing content", contentStore.exists(contentUrl));

                final ContentReader properReader = contentStore.getReader(contentUrl);
                Assert.assertTrue("Reader was not returned for freshly written content", properReader != null);
                Assert.assertTrue("Reader does not refer to existing file for freshly written content", properReader.exists());

                // reader does not know about mimetype (provided via persisted ContentData at server runtime)
                properReader.setMimetype(MimetypeMap.MIMETYPE_TEXT_PLAIN);

                final String readText = properReader.getContentString();
                Assert.assertEquals("Read content does not match written test content", testText, readText);

                return writer;
            }
        });
    }

    private static String generateText(final long seed)
    {
        final Lorem lorem = new LoremIpsum(Long.valueOf(seed));
        final String text = lorem.getParagraphs(5, 25);
        return text;
    }
}
