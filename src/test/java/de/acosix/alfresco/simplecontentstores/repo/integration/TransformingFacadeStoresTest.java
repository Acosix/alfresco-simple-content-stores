/*
 * Copyright 2017 - 2024 Acosix GmbH
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
package de.acosix.alfresco.simplecontentstores.repo.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.thedeanda.lorem.LoremIpsum;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.ws.rs.NotFoundException;

import org.apache.commons.io.IOUtils;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import de.acosix.alfresco.rest.client.api.NodesV1;
import de.acosix.alfresco.rest.client.api.PeopleV1;
import de.acosix.alfresco.rest.client.model.nodes.NodeCreationRequestEntity;
import de.acosix.alfresco.rest.client.model.nodes.NodeResponseEntity;
import de.acosix.alfresco.rest.client.model.people.PersonRequestEntity;

/**
 * The tests in this class are meant to cover any of the generic content store facades that apply transformative operations on content
 * before it is stored via actual, backing content stores.
 *
 * @author Axel Faust
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TransformingFacadeStoresTest extends AbstractStoresTest
{

    private static ResteasyClient client;

    private static final String testUser = "test";

    private static final String testUserPassword = "test";

    @BeforeClass
    public static void setup()
    {
        client = setupResteasyClient();

        final String ticket = obtainTicket(client, baseUrl, "admin", "admin");
        final PeopleV1 people = createAPI(client, baseUrl, PeopleV1.class, ticket);

        try
        {
            people.getPerson(testUser);
        }
        catch (final NotFoundException nfe)
        {
            final PersonRequestEntity personToCreate = new PersonRequestEntity();
            personToCreate.setEmail(testUser + "@example.com");
            personToCreate.setFirstName("Test");
            personToCreate.setLastName("Guy");
            personToCreate.setId(testUser);
            personToCreate.setPassword(testUserPassword);
            people.createPerson(personToCreate);
        }
    }

    @Test
    public void compressingFacadeStore() throws IOException
    {
        // need to record pre-existing files to exclude in verification
        final Collection<ContentFile> knownFiles = listFilesInAlfData("compressingFileFacadeStore");

        final String ticket = obtainTicket(client, baseUrl, testUser, testUserPassword);
        final NodesV1 nodes = createAPI(client, baseUrl, NodesV1.class, ticket);

        final String documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "compressing-file-facade",
                "Compressing File Facade Site");

        final NodeCreationRequestEntity createRequest = new NodeCreationRequestEntity();
        createRequest.setName(UUID.randomUUID().toString());
        createRequest.setNodeType("cm:content");

        NodeResponseEntity createdNode = nodes.createNode(documentLibraryNodeId, createRequest);

        // tests generic text/* pattern for compressible mimetypes
        byte[] contentBytes = LoremIpsum.getInstance().getParagraphs(4, 20).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        ContentFile lastModifiedFileInContent = findLastModifiedFileInAlfData("compressingFileFacadeStore", knownFiles);

        assertNotNull(lastModifiedFileInContent);
        knownFiles.add(lastModifiedFileInContent);
        assertTrue(contentBytes.length > lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // tests/verifies generic text/* pattern for compressible mimetypes
        createRequest.setName(UUID.randomUUID().toString());

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);

        contentBytes = LoremIpsum.getInstance().getHtmlParagraphs(4, 20).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/html");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("compressingFileFacadeStore", knownFiles);

        assertNotNull(lastModifiedFileInContent);
        knownFiles.add(lastModifiedFileInContent);
        assertTrue(contentBytes.length > lastModifiedFileInContent.getSizeInContainer());
        assertFalse(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // tests explicit application/json pattern for compressible mimetypes
        createRequest.setName(UUID.randomUUID().toString());

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);

        try (InputStream is = this.getClass().getClassLoader().getResourceAsStream("testContentFiles/random.json"))
        {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream(512);
            IOUtils.copy(is, baos);
            contentBytes = baos.toByteArray();
        }
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "application/json");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("compressingFileFacadeStore", knownFiles);

        assertNotNull(lastModifiedFileInContent);
        knownFiles.add(lastModifiedFileInContent);
        assertTrue(contentBytes.length > lastModifiedFileInContent.getSizeInContainer());
        assertFalse(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // test non-compressible file type
        createRequest.setName(UUID.randomUUID().toString());

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);

        try (InputStream is = this.getClass().getClassLoader().getResourceAsStream("testContentFiles/sample.pdf"))
        {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream(512);
            IOUtils.copy(is, baos);
            contentBytes = baos.toByteArray();
        }
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "application/pdf");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("compressingFileFacadeStore", knownFiles);

        assertNotNull(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));
    }

    @Test
    public void deduplicatingFacadeStore() throws IOException
    {
        // need to record pre-existing files to exclude in verification
        final Collection<ContentFile> knownFiles = listFilesInAlfData("deduplicatingFileFacadeStore");

        final String ticket = obtainTicket(client, baseUrl, testUser, testUserPassword);
        final NodesV1 nodes = createAPI(client, baseUrl, NodesV1.class, ticket);

        final String documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "deduplicating-file-facade",
                "Deduplicating File Facade Site");

        final NodeCreationRequestEntity createRequest = new NodeCreationRequestEntity();
        createRequest.setName(UUID.randomUUID().toString());
        createRequest.setNodeType("cm:content");

        final NodeResponseEntity createdNode1 = nodes.createNode(documentLibraryNodeId, createRequest);

        createRequest.setName(UUID.randomUUID().toString());
        createRequest.setNodeType("cm:content");

        final NodeResponseEntity createdNode2 = nodes.createNode(documentLibraryNodeId, createRequest);

        final byte[] contentBytes = LoremIpsum.getInstance().getParagraphs(4, 20).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode1.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        ContentFile lastModifiedFileInContent = findLastModifiedFileInAlfData("deduplicatingFileFacadeStore", knownFiles);

        assertNotNull(lastModifiedFileInContent);
        knownFiles.add(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode1.getId())));

        // test no new content file is created when exact same content is stored for another node
        nodes.setContent(createdNode2.getId(), new ByteArrayInputStream(contentBytes), "text/html");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("deduplicatingFileFacadeStore", knownFiles);

        assertNull(lastModifiedFileInContent);
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode2.getId())));

        // test new content file is written for a minimal change in content
        final int idxToChange = new SecureRandom().nextInt(contentBytes.length);
        contentBytes[idxToChange] = (byte) (contentBytes[idxToChange] + 1);
        nodes.setContent(createdNode2.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("deduplicatingFileFacadeStore", knownFiles);

        assertNotNull(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode2.getId())));
    }

    @Test
    // we need to order encryption tests as key management / state is global and some tests can impact others
    // would be simpler if we were to re-create Alfresco from scratch for every test, but that would be excessive
    // order is based on method name (ascending)
    public void encryption0WithExpectedMasterKeys()
    {
        final SimpleContentStoresCommandPlugin commandConsolePlugin = createAPI(client, baseUrl, SimpleContentStoresCommandPlugin.class,
                "admin", "admin");

        final List<String> activeKeyList = commandConsolePlugin.listEncryptionKeys(CommandConsolePluginRequest.from("active"))
                .getPreformattedOutputLines();
        final List<String> inactiveKeyList = commandConsolePlugin.listEncryptionKeys(CommandConsolePluginRequest.from("inactive"))
                .getPreformattedOutputLines();

        assertEquals(4, activeKeyList.size());
        assertEquals(1, inactiveKeyList.size());
        assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-effs:effs")));
        assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-aesjks:firstkey")));
        assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-aesks:effs-aes ")));
        assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-aesks:effs-aes2")));
        assertTrue(inactiveKeyList.contains("No keys found"));
    }

    @Test
    // we need to order encryption tests as key management / state is global and some tests can impact others
    // would be simpler if we were to re-create Alfresco from scratch for every test, but that would be excessive
    // order is based on method name (ascending)
    public void encryption1WithDifferentMasterKeys()
    {
        final String ticket = obtainTicket(client, baseUrl, testUser, testUserPassword);
        final NodesV1 nodes = createAPI(client, baseUrl, NodesV1.class, ticket);
        final SimpleContentStoresCommandPlugin commandConsolePlugin = createAPI(client, baseUrl, SimpleContentStoresCommandPlugin.class,
                "admin", "admin");

        // there should not be any encrypted data yet
        final List<String> initialCounts = commandConsolePlugin.countEncryptedSymmetricKeys(CommandConsolePluginRequest.from())
                .getPreformattedOutputLines();
        assertEquals(1, initialCounts.size());
        assertTrue(initialCounts.contains("No symmetric keys found"));

        final String documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "encrypting-file-aes-facade",
                "Encrypting File AES Facade Site");

        int contentCount = 200;
        this.createRandomContents(contentCount, nodes, documentLibraryNodeId);

        // master key usage is random, but with four configured keys and 200 contents created, each key should be used at least once
        final List<String> updatedCounts = commandConsolePlugin.countEncryptedSymmetricKeys(CommandConsolePluginRequest.from())
                .getPreformattedOutputLines();
        assertEquals(4, updatedCounts.size());

        int combinedCount = 0;
        final List<String> keys = new ArrayList<>(3);
        for (final String countLine : updatedCounts)
        {
            final String count = countLine.substring(0, countLine.indexOf(' '));
            combinedCount += Integer.parseInt(count);
            final String key = countLine.substring(countLine.lastIndexOf(" by ") + 4);
            keys.add(key);
        }

        assertEquals(contentCount, combinedCount);
        assertTrue(keys.contains("scs-effs:effs"));
        assertTrue(keys.contains("scs-aesjks:firstkey"));
        assertTrue(keys.contains("scs-aesks:effs-aes"));
        assertTrue(keys.contains("scs-aesks:effs-aes2"));
    }

    @Test
    // we need to order encryption tests as key management / state is global and some tests can impact others
    // would be simpler if we were to re-create Alfresco from scratch for every test, but that would be excessive
    // order is based on method name (ascending)
    public void encryption2WithDifferentSymmetricKeys() throws IOException
    {
        // need to record pre-existing files to exclude in verification
        final Collection<ContentFile> knownFiles = listFilesInAlfData("encryptingFileAESFacadeStore");

        final String ticket = obtainTicket(client, baseUrl, testUser, testUserPassword);
        final NodesV1 nodes = createAPI(client, baseUrl, NodesV1.class, ticket);

        final String documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "encrypting-file-aes-facade",
                "Encrypting File AES Facade Site");

        final NodeCreationRequestEntity createRequest = new NodeCreationRequestEntity();
        createRequest.setName(UUID.randomUUID().toString());
        createRequest.setNodeType("cm:content");

        final NodeResponseEntity createdNode1 = nodes.createNode(documentLibraryNodeId, createRequest);

        createRequest.setName(UUID.randomUUID().toString());
        createRequest.setNodeType("cm:content");

        final NodeResponseEntity createdNode2 = nodes.createNode(documentLibraryNodeId, createRequest);

        final byte[] contentBytes = LoremIpsum.getInstance().getParagraphs(4, 20).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode1.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        final ContentFile lastModifiedFileInContent1 = findLastModifiedFileInAlfData("encryptingFileAESFacadeStore", knownFiles);

        assertNotNull(lastModifiedFileInContent1);
        knownFiles.add(lastModifiedFileInContent1);
        assertNotEquals(contentBytes.length, lastModifiedFileInContent1.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode1.getId())));

        // test new content file with different encryption result (due to different symmetric key per content) is created when exact same
        // content is stored for another node
        nodes.setContent(createdNode2.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        final ContentFile lastModifiedFileInContent2 = findLastModifiedFileInAlfData("encryptingFileAESFacadeStore", knownFiles);

        assertNotNull(lastModifiedFileInContent2);
        assertNotEquals(contentBytes.length, lastModifiedFileInContent2.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode2.getId())));

        assertFalse(contentMatches(lastModifiedFileInContent1, lastModifiedFileInContent2));
        // overall length after encrypting must still be the same due to same algorithm / block size
        assertEquals(lastModifiedFileInContent1.getSizeInContainer(), lastModifiedFileInContent2.getSizeInContainer());
    }

    @Test
    // we need to order encryption tests as key management / state is global and some tests can impact others
    // would be simpler if we were to re-create Alfresco from scratch for every test, but that would be excessive
    // order is based on method name (ascending)
    public void encryption3WithDisabledKey()
    {
        final String ticket = obtainTicket(client, baseUrl, testUser, testUserPassword);
        final NodesV1 nodes = createAPI(client, baseUrl, NodesV1.class, ticket);
        final SimpleContentStoresCommandPlugin commandConsolePlugin = createAPI(client, baseUrl, SimpleContentStoresCommandPlugin.class,
                "admin", "admin");

        List<String> activeKeyList = commandConsolePlugin.listEncryptionKeys(CommandConsolePluginRequest.from("active"))
                .getPreformattedOutputLines();
        List<String> inactiveKeyList = commandConsolePlugin.listEncryptionKeys(CommandConsolePluginRequest.from("inactive"))
                .getPreformattedOutputLines();

        assertEquals(4, activeKeyList.size());
        assertEquals(1, inactiveKeyList.size());
        assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-effs:effs")));
        assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-aesjks:firstkey")));
        assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-aesks:effs-aes ")));
        assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-aesks:effs-aes2")));
        assertTrue(inactiveKeyList.contains("No keys found"));

        final String documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "encrypting-file-aes-facade",
                "Encrypting File AES Facade Site");

        this.createRandomContents(30, nodes, documentLibraryNodeId);

        final List<String> referenceCounts = commandConsolePlugin
                .countEncryptedSymmetricKeys(CommandConsolePluginRequest.from("scs-aesks:effs-aes2")).getPreformattedOutputLines();
        final String referenceCountLine = referenceCounts.get(0);
        assertNotEquals("No symmetric keys found", referenceCountLine);
        final int referenceCount = Integer.parseInt(referenceCountLine.substring(0, referenceCountLine.indexOf(' ')));

        commandConsolePlugin.disableEncryptionKey(CommandConsolePluginRequest.from("scs-aesks:effs-aes2"));

        activeKeyList = commandConsolePlugin.listEncryptionKeys(CommandConsolePluginRequest.from("active")).getPreformattedOutputLines();
        inactiveKeyList = commandConsolePlugin.listEncryptionKeys(CommandConsolePluginRequest.from("inactive"))
                .getPreformattedOutputLines();

        assertEquals(3, activeKeyList.size());
        assertEquals(1, inactiveKeyList.size());
        assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-effs:effs")));
        assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-aesjks:firstkey")));
        assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-aesks:effs-aes ")));
        assertFalse(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-aesks:effs-aes2")));
        assertTrue(inactiveKeyList.stream().anyMatch(line -> line.startsWith("scs-aesks:effs-aes2")));

        this.createRandomContents(30, nodes, documentLibraryNodeId);

        final List<String> updatedCounts = commandConsolePlugin
                .countEncryptedSymmetricKeys(CommandConsolePluginRequest.from("scs-aesks:effs-aes2")).getPreformattedOutputLines();
        final String updatedCountLine = updatedCounts.get(0);
        final int updatedCount = Integer.parseInt(updatedCountLine.substring(0, updatedCountLine.indexOf(' ')));

        assertEquals(referenceCount, updatedCount);

        commandConsolePlugin.enableEncryptionKey(CommandConsolePluginRequest.from("scs-aesks:effs-aes2"));

        activeKeyList = commandConsolePlugin.listEncryptionKeys(CommandConsolePluginRequest.from("active")).getPreformattedOutputLines();
        inactiveKeyList = commandConsolePlugin.listEncryptionKeys(CommandConsolePluginRequest.from("inactive"))
                .getPreformattedOutputLines();

        assertEquals(4, activeKeyList.size());
        assertEquals(1, inactiveKeyList.size());
        assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-effs:effs")));
        assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-aesjks:firstkey")));
        assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-aesks:effs-aes ")));
        assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-aesks:effs-aes2")));
        assertTrue(inactiveKeyList.contains("No keys found"));

        this.createRandomContents(30, nodes, documentLibraryNodeId);

        final List<String> finalCounts = commandConsolePlugin
                .countEncryptedSymmetricKeys(CommandConsolePluginRequest.from("scs-aesks:effs-aes2")).getPreformattedOutputLines();
        final String finalCountLine = finalCounts.get(0);
        final int finalCount = Integer.parseInt(finalCountLine.substring(0, finalCountLine.indexOf(' ')));

        assertNotEquals(referenceCount, finalCount);
    }

    @Test
    // we need to order encryption tests as key management / state is global and some tests can impact others
    // would be simpler if we were to re-create Alfresco from scratch for every test, but that would be excessive
    // order is based on method name (ascending)
    public void encryption4WithReEncryption()
    {
        final String ticket = obtainTicket(client, baseUrl, testUser, testUserPassword);
        final NodesV1 nodes = createAPI(client, baseUrl, NodesV1.class, ticket);
        final SimpleContentStoresCommandPlugin commandConsolePlugin = createAPI(client, baseUrl, SimpleContentStoresCommandPlugin.class,
                "admin", "admin");

        List<String> activeKeyList = commandConsolePlugin.listEncryptionKeys(CommandConsolePluginRequest.from("active"))
                .getPreformattedOutputLines();
        List<String> inactiveKeyList = commandConsolePlugin.listEncryptionKeys(CommandConsolePluginRequest.from("inactive"))
                .getPreformattedOutputLines();
        List<String> eligibleKeyList = commandConsolePlugin.listEncryptionKeysEligibleForReEncryption(CommandConsolePluginRequest.from())
                .getPreformattedOutputLines();

        assertEquals(4, activeKeyList.size());
        assertEquals(1, inactiveKeyList.size());
        assertEquals(1, eligibleKeyList.size());
        assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-effs:effs")));
        assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-aesjks:firstkey")));
        assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-aesks:effs-aes ")));
        assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-aesks:effs-aes2")));
        assertTrue(inactiveKeyList.contains("No keys found"));
        assertTrue(eligibleKeyList.contains("No keys found"));

        final String documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "encrypting-file-aes-facade",
                "Encrypting File AES Facade Site");

        this.createRandomContents(30, nodes, documentLibraryNodeId);

        final List<String> referenceCounts = commandConsolePlugin
                .countEncryptedSymmetricKeys(CommandConsolePluginRequest.from("scs-aesks:effs-aes2")).getPreformattedOutputLines();
        final String referenceCountLine = referenceCounts.get(0);
        assertNotEquals("No symmetric keys found", referenceCountLine);

        commandConsolePlugin.disableEncryptionKey(CommandConsolePluginRequest.from("scs-aesks:effs-aes2"));

        activeKeyList = commandConsolePlugin.listEncryptionKeys(CommandConsolePluginRequest.from("active")).getPreformattedOutputLines();
        inactiveKeyList = commandConsolePlugin.listEncryptionKeys(CommandConsolePluginRequest.from("inactive"))
                .getPreformattedOutputLines();
        eligibleKeyList = commandConsolePlugin.listEncryptionKeysEligibleForReEncryption(CommandConsolePluginRequest.from())
                .getPreformattedOutputLines();

        assertEquals(3, activeKeyList.size());
        assertEquals(1, inactiveKeyList.size());
        assertEquals(1, eligibleKeyList.size());
        assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-effs:effs ")));
        assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-aesjks:firstkey")));
        assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-aesks:effs-aes ")));
        assertFalse(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-aesks:effs-aes2 ")));
        assertTrue(inactiveKeyList.stream().anyMatch(line -> line.startsWith("scs-aesks:effs-aes2 ")));
        assertTrue(eligibleKeyList.stream().anyMatch(line -> line.startsWith("scs-aesks:effs-aes2 ")));

        commandConsolePlugin.reEncryptSymmetricKeys(CommandConsolePluginRequest.from("scs-aesks:effs-aes2"));

        final List<String> updatedCounts = commandConsolePlugin
                .countEncryptedSymmetricKeys(CommandConsolePluginRequest.from("scs-aesks:effs-aes2")).getPreformattedOutputLines();
        final String updatedCountLine = updatedCounts.get(0);
        assertEquals("No symmetric keys found", updatedCountLine);
    }

    @Test
    public void encryption5AESMode() throws IOException
    {
        final String ticket = obtainTicket(client, baseUrl, testUser, testUserPassword);
        final NodesV1 nodes = createAPI(client, baseUrl, NodesV1.class, ticket);

        testMode("AES", ticket, nodes);
    }

    @Test
    public void encryption5DESMode() throws IOException
    {
        final String ticket = obtainTicket(client, baseUrl, testUser, testUserPassword);
        final NodesV1 nodes = createAPI(client, baseUrl, NodesV1.class, ticket);

        testMode("DES", ticket, nodes);
    }

    @Test
    public void encryption5DESedeMode() throws IOException
    {
        final String ticket = obtainTicket(client, baseUrl, testUser, testUserPassword);
        final NodesV1 nodes = createAPI(client, baseUrl, NodesV1.class, ticket);

        testMode("DESede", ticket, nodes);
    }

    protected void testMode(String mode, String ticket, NodesV1 nodes) throws IOException
    {
        String storeName = "encryptingFile" + mode + "FacadeStore";
        final Collection<ContentFile> knownFiles = listFilesInAlfData(storeName);

        final String documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket,
                "encrypting-file-" + mode.toLowerCase(Locale.ENGLISH) + "-facade", "Encrypting File " + mode + "Facade Site");

        final NodeCreationRequestEntity createRequest = new NodeCreationRequestEntity();
        createRequest.setName(UUID.randomUUID().toString());
        createRequest.setNodeType("cm:content");

        final NodeResponseEntity createdNode = nodes.createNode(documentLibraryNodeId, createRequest);
        final byte[] contentBytes = LoremIpsum.getInstance().getParagraphs(4, 20).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        final ContentFile lastModifiedFileInContent = findLastModifiedFileInAlfData(storeName, knownFiles);

        assertNotNull(lastModifiedFileInContent);
        assertNotEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));
    }

    protected void createRandomContents(final int maxCount, final NodesV1 nodes, final String documentLibraryNodeId)
    {
        for (int count = 0; count < maxCount; count++)
        {
            final NodeCreationRequestEntity createRequest = new NodeCreationRequestEntity();
            createRequest.setName(UUID.randomUUID().toString());
            createRequest.setNodeType("cm:content");

            final NodeResponseEntity createdNode = nodes.createNode(documentLibraryNodeId, createRequest);
            final byte[] contentBytes = LoremIpsum.getInstance().getParagraphs(4, 20).getBytes(StandardCharsets.UTF_8);
            nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");
        }
    }
}
