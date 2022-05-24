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
package de.acosix.alfresco.simplecontentstores.repo;

import com.thedeanda.lorem.Lorem;
import com.thedeanda.lorem.LoremIpsum;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.alfresco.repo.content.ContentContext;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import de.acosix.alfresco.simplecontentstores.repo.store.StoreConstants;
import de.acosix.alfresco.simplecontentstores.repo.store.context.ContentStoreContext;
import de.acosix.alfresco.simplecontentstores.repo.store.file.FileContentStore;

/**
 *
 * @author Axel Faust
 */
public class FileContentStoreTest
{

    private static final String STORE_PROTOCOL = FileContentStoreTest.class.getSimpleName();

    private static final SecureRandom SEED_PRNG;
    static
    {
        try
        {
            SEED_PRNG = new SecureRandom(DeduplicatingContentStoreTest.class.getName().getBytes(StandardCharsets.UTF_8.name()));
        }
        catch (final UnsupportedEncodingException ex)
        {
            throw new RuntimeException("Java does not support UTF-8 anymore, so run for your lives...", ex);
        }
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private File storeFolder;

    private File linkedFolder;

    @Before
    public void setup() throws IOException
    {
        this.storeFolder = TestUtilities.createFolder();
    }

    @After
    public void tearDown()
    {
        TestUtilities.delete(this.storeFolder);
        if (this.linkedFolder != null)
        {
            TestUtilities.delete(this.linkedFolder);
        }
    }

    @Test
    public void unconfiguredWriteReadDelete() throws Exception
    {
        final FileContentStore store = this.createDefaultStore();

        store.afterPropertiesSet();

        Assert.assertTrue("Store should support write", store.isWriteSupported());

        final String testText = generateText(SEED_PRNG.nextLong());
        final Date dateBeforeWrite = new Date();
        final ContentWriter writer = this.testIndividualWriteAndRead(store, testText);

        final String contentUrl = writer.getContentUrl();
        final DateFormat df = new SimpleDateFormat("yyyy/M/d/H/m", Locale.ENGLISH);
        df.setTimeZone(TimeZone.getDefault());
        final String expectedPattern = "^" + STORE_PROTOCOL + ContentStore.PROTOCOL_DELIMITER + df.format(dateBeforeWrite)
                + "/[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}\\.bin$";
        Assert.assertTrue("Content URL did not match expected date-based pattern with UUID", contentUrl.matches(expectedPattern));

        Assert.assertTrue("Content should have been deleted", store.delete(contentUrl));
        final Path rootPath = this.storeFolder.toPath();
        final long subPathCount = TestUtilities.walkAndProcess(rootPath, stream -> stream.filter(path -> {
            return !path.equals(rootPath);
        }).count(), FileVisitOption.FOLLOW_LINKS);
        Assert.assertEquals("Store path should not contain any elements after delete", 0, subPathCount);
    }

    @Test
    public void dontDeleteEmptryDirs() throws Exception
    {
        final FileContentStore store = this.createDefaultStore();
        store.setDeleteEmptyDirs(false);

        store.afterPropertiesSet();

        Assert.assertTrue("Store should support write", store.isWriteSupported());

        final String testText = generateText(SEED_PRNG.nextLong());
        final ContentWriter writer = this.testIndividualWriteAndRead(store, testText);

        final String contentUrl = writer.getContentUrl();
        Assert.assertTrue("Content should have been deleted", store.delete(contentUrl));
        final Path rootPath = this.storeFolder.toPath();
        final long subPathCount = TestUtilities.walkAndProcess(rootPath, stream -> stream.filter(path -> {
            return !path.equals(rootPath);
        }).count(), FileVisitOption.FOLLOW_LINKS);
        Assert.assertNotEquals("Store path should contain additional elements after delete without allowing empty directory deletion", 0,
                subPathCount);

        final long filesCount = TestUtilities.walkAndProcess(rootPath, stream -> stream.filter(path -> {
            return path.toFile().isFile();
        }).count(), FileVisitOption.FOLLOW_LINKS);
        Assert.assertEquals("Store path should not contain any content files after deletion", 0, filesCount);
    }

    // TODO Don't run test on Windows systems - no support for symbolic links
    @Test
    @Ignore("Fails to run on Windows OS")
    public void deleteEmptyParentsButNotSymbolicLinks() throws Exception
    {
        this.linkedFolder = TestUtilities.createFolder();

        final DateFormat df = new SimpleDateFormat("yyyy/M/d", Locale.ENGLISH);
        df.setTimeZone(TimeZone.getDefault());
        final String relativePathForSymbolicLink = df.format(new Date());
        final String relativePathForFolder = relativePathForSymbolicLink.substring(0, relativePathForSymbolicLink.lastIndexOf('/'));
        final String linkName = relativePathForSymbolicLink.substring(relativePathForSymbolicLink.lastIndexOf('/') + 1);

        final Path folderForLink = Files.createDirectories(this.storeFolder.toPath().resolve(relativePathForFolder));
        final Path linkPath = folderForLink.resolve(linkName);
        Files.createSymbolicLink(linkPath, this.linkedFolder.toPath());

        final FileContentStore store = this.createDefaultStore();

        store.afterPropertiesSet();

        Assert.assertTrue("Store should support write", store.isWriteSupported());

        final String testText = generateText(SEED_PRNG.nextLong());
        final ContentWriter writer = this.testIndividualWriteAndRead(store, testText);
        final String contentUrl = writer.getContentUrl();
        Assert.assertTrue("Content should have been deleted", store.delete(contentUrl));

        final Path linkedRootPath = this.linkedFolder.toPath();
        final long linkedFolderSubPaths = TestUtilities.walkAndProcess(linkedRootPath, stream -> stream.filter(path -> {
            return !path.equals(linkedRootPath);
        }).count(), FileVisitOption.FOLLOW_LINKS);
        Assert.assertEquals("Linked folder should not contain additional elements after delete", 0, linkedFolderSubPaths);

        Assert.assertTrue("Link should still exist after delete", Files.exists(linkPath));
    }

    @Test
    public void predeterminedContentURL() throws Exception
    {
        final FileContentStore store = this.createDefaultStore();

        store.afterPropertiesSet();

        Assert.assertTrue("Store should support write", store.isWriteSupported());

        final String testText = generateText(SEED_PRNG.nextLong());
        final String dummyContentUrl = STORE_PROTOCOL + ContentStore.PROTOCOL_DELIMITER + "any/path/will/do";
        final ContentWriter writer = this.testIndividualWriteAndRead(store, new ContentContext(null, dummyContentUrl), testText);

        final String contentUrl = writer.getContentUrl();
        Assert.assertEquals("Effective content URL did not match provided URL", dummyContentUrl, contentUrl);
    }

    @Test
    public void wildcardContentURL() throws Exception
    {
        final FileContentStore store = this.createDefaultStore();

        store.afterPropertiesSet();

        Assert.assertTrue("Store should support write", store.isWriteSupported());

        final String testText = generateText(SEED_PRNG.nextLong());
        final String dummyContentUrl = StoreConstants.WILDCARD_PROTOCOL + ContentStore.PROTOCOL_DELIMITER + "any/path/will/do";
        final String expectedContentUrl = STORE_PROTOCOL + ContentStore.PROTOCOL_DELIMITER + "any/path/will/do";
        final ContentWriter writer = this.testIndividualWriteAndRead(store, new ContentContext(null, dummyContentUrl), testText);

        final String contentUrl = writer.getContentUrl();
        Assert.assertEquals("Effective content URL did not match expected URL", expectedContentUrl, contentUrl);

        Assert.assertTrue("Wildcard-based content URL should have been reported as supported",
                store.isContentUrlSupported(dummyContentUrl));
        Assert.assertTrue("Wildcard-based content URL should have been reported as existing", store.exists(dummyContentUrl));

        final ContentReader reader = store.getReader(dummyContentUrl);
        Assert.assertNotNull("Wildcard-based content URL should have yielded a reader", reader);
        Assert.assertTrue("Wildcard-based content URL should have yielded a reader to existing content", reader.exists());
        final String readContent = reader.getContentString();
        Assert.assertEquals("Content read from reader for wildcard-based content URL did not match written content", testText, readContent);

        Assert.assertTrue("Content should have been deleted using wildcard-based content URL", store.delete(dummyContentUrl));
        Assert.assertFalse(
                "Content should not be reported as existing for explicit content URL after having been deleted via wildcard-based content URL",
                store.exists(contentUrl));
    }

    @Test
    public void readOnlyWrite()
    {
        final FileContentStore store = this.createDefaultStore();
        store.setReadOnly(true);

        store.afterPropertiesSet();

        Assert.assertFalse("Store should not support write", store.isWriteSupported());

        final String testText = generateText(SEED_PRNG.nextLong());
        final String dummyContentUrl = STORE_PROTOCOL + ContentStore.PROTOCOL_DELIMITER + "any/path/will/do";
        this.thrown.expect(UnsupportedOperationException.class);
        this.testIndividualWriteAndRead(store, new ContentContext(null, dummyContentUrl), testText);
    }

    @Test
    public void readOnlyDelete()
    {
        final FileContentStore store = this.createDefaultStore();
        store.setReadOnly(true);

        store.afterPropertiesSet();

        Assert.assertFalse("Store should not support write", store.isWriteSupported());

        final String dummyContentUrl = STORE_PROTOCOL + ContentStore.PROTOCOL_DELIMITER + "any/path/will/do";
        this.thrown.expect(UnsupportedOperationException.class);
        store.delete(dummyContentUrl);
    }

    private FileContentStore createDefaultStore()
    {
        final FileContentStore store = new FileContentStore();
        store.setRootDirectory(this.storeFolder.getAbsolutePath());
        store.setProtocol(STORE_PROTOCOL);

        return store;
    }

    private ContentWriter testIndividualWriteAndRead(final FileContentStore fileContentStore, final String testText)
    {
        return this.testIndividualWriteAndRead(fileContentStore, new ContentContext(null, null), testText);
    }

    private ContentWriter testIndividualWriteAndRead(final FileContentStore fileContentStore, final ContentContext context,
            final String testText)
    {
        return ContentStoreContext.executeInNewContext(() -> {
            final ContentWriter writer = fileContentStore.getWriter(context);
            writer.setMimetype(MimetypeMap.MIMETYPE_TEXT_PLAIN);
            writer.setEncoding(StandardCharsets.UTF_8.name());
            writer.setLocale(Locale.ENGLISH);
            writer.putContent(testText);

            final String contentUrl = writer.getContentUrl();
            Assert.assertNotNull("Content URL was not set after writing content", contentUrl);
            Assert.assertTrue("Content URL does not start with the configured protocol",
                    contentUrl.startsWith(STORE_PROTOCOL + ContentStore.PROTOCOL_DELIMITER));

            Assert.assertTrue("Store does not report content URL to exist after writing content", fileContentStore.exists(contentUrl));

            final String relativePath = contentUrl
                    .substring(contentUrl.indexOf(ContentStore.PROTOCOL_DELIMITER) + ContentStore.PROTOCOL_DELIMITER.length());
            final Path rootPath = this.storeFolder.toPath();
            final File file = rootPath.resolve(relativePath).toFile();
            Assert.assertTrue("File should be stored in literal path from content URL", file.exists());

            final ContentReader properReader = fileContentStore.getReader(contentUrl);
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
