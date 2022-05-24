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
package de.acosix.alfresco.simplecontentstores.repo.integration;

import com.thedeanda.lorem.LoremIpsum;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.NotFoundException;

import org.apache.commons.io.IOUtils;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
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

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

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
        final Collection<Path> exclusions = listFilesInAlfData("compressingFileFacadeStore");

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

        Path lastModifiedFileInContent = findLastModifiedFileInAlfData("compressingFileFacadeStore", exclusions);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertTrue(contentBytes.length > Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // tests/verifies generic text/* pattern for compressible mimetypes
        createRequest.setName(UUID.randomUUID().toString());

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);

        contentBytes = LoremIpsum.getInstance().getHtmlParagraphs(4, 20).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/html");

        exclusions.add(lastModifiedFileInContent);
        lastModifiedFileInContent = findLastModifiedFileInAlfData("compressingFileFacadeStore", exclusions);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertTrue(contentBytes.length > Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

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

        exclusions.add(lastModifiedFileInContent);
        lastModifiedFileInContent = findLastModifiedFileInAlfData("compressingFileFacadeStore", exclusions);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertTrue(contentBytes.length > Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

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

        exclusions.add(lastModifiedFileInContent);
        lastModifiedFileInContent = findLastModifiedFileInAlfData("compressingFileFacadeStore", exclusions);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));
    }

    @Test
    public void deduplicatingFacadeStore() throws IOException
    {
        // need to record pre-existing files to exclude in verification
        final Collection<Path> exclusions = listFilesInAlfData("deduplicatingFileFacadeStore");

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

        Path lastModifiedFileInContent = findLastModifiedFileInAlfData("deduplicatingFileFacadeStore", exclusions);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode1.getId())));

        // test no new content file is created when exact same content is stored for another node
        nodes.setContent(createdNode2.getId(), new ByteArrayInputStream(contentBytes), "text/html");

        exclusions.add(lastModifiedFileInContent);
        lastModifiedFileInContent = findLastModifiedFileInAlfData("deduplicatingFileFacadeStore", exclusions);

        Assert.assertNull(lastModifiedFileInContent);
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode2.getId())));

        // test new content file is written for a minimal change in content
        final int idxToChange = new SecureRandom().nextInt(contentBytes.length);
        contentBytes[idxToChange] = (byte) (contentBytes[idxToChange] + 1);
        nodes.setContent(createdNode2.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("deduplicatingFileFacadeStore", exclusions);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode2.getId())));
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

        Assert.assertEquals(3, activeKeyList.size());
        Assert.assertEquals(1, inactiveKeyList.size());
        Assert.assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-effs:effs")));
        Assert.assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-aesks:effs-aes ")));
        Assert.assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-aesks:effs-aes2")));
        Assert.assertTrue(inactiveKeyList.contains("No keys found"));
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
        Assert.assertEquals(1, initialCounts.size());
        Assert.assertTrue(initialCounts.contains("No symmetric keys found"));

        final String documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "encrypting-file-facade",
                "Encrypting File Facade Site");

        this.createRandomContents(30, nodes, documentLibraryNodeId);

        // master key usage is random, but with three configured keys and 30 contents created, each key should be used at least once
        final List<String> updatedCounts = commandConsolePlugin.countEncryptedSymmetricKeys(CommandConsolePluginRequest.from())
                .getPreformattedOutputLines();
        Assert.assertEquals(3, updatedCounts.size());

        int combinedCount = 0;
        final List<String> keys = new ArrayList<>(3);
        for (final String countLine : updatedCounts)
        {
            final String count = countLine.substring(0, countLine.indexOf(' '));
            combinedCount += Integer.parseInt(count);
            final String key = countLine.substring(countLine.lastIndexOf(" by ") + 4);
            keys.add(key);
        }

        Assert.assertEquals(30, combinedCount);
        Assert.assertTrue(keys.contains("scs-effs:effs"));
        Assert.assertTrue(keys.contains("scs-aesks:effs-aes"));
        Assert.assertTrue(keys.contains("scs-aesks:effs-aes2"));
    }

    @Test
    // we need to order encryption tests as key management / state is global and some tests can impact others
    // would be simpler if we were to re-create Alfresco from scratch for every test, but that would be excessive
    // order is based on method name (ascending)
    public void encryption2WithDifferentSymmetricKeys() throws IOException
    {
        // need to record pre-existing files to exclude in verification
        final Collection<Path> exclusions = listFilesInAlfData("encryptingFileFacadeStore");

        final String ticket = obtainTicket(client, baseUrl, testUser, testUserPassword);
        final NodesV1 nodes = createAPI(client, baseUrl, NodesV1.class, ticket);

        final String documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "encrypting-file-facade",
                "Encrypting File Facade Site");

        final NodeCreationRequestEntity createRequest = new NodeCreationRequestEntity();
        createRequest.setName(UUID.randomUUID().toString());
        createRequest.setNodeType("cm:content");

        final NodeResponseEntity createdNode1 = nodes.createNode(documentLibraryNodeId, createRequest);

        createRequest.setName(UUID.randomUUID().toString());
        createRequest.setNodeType("cm:content");

        final NodeResponseEntity createdNode2 = nodes.createNode(documentLibraryNodeId, createRequest);

        final byte[] contentBytes = LoremIpsum.getInstance().getParagraphs(4, 20).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode1.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        final Path lastModifiedFileInContent1 = findLastModifiedFileInAlfData("encryptingFileFacadeStore", exclusions);

        Assert.assertNotNull(lastModifiedFileInContent1);
        Assert.assertNotEquals(contentBytes.length, Files.size(lastModifiedFileInContent1));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode1.getId())));

        // test new content file with different encryption result (due to different symmetric key per content) is created when exact same
        // content is stored for another node
        nodes.setContent(createdNode2.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        exclusions.add(lastModifiedFileInContent1);
        final Path lastModifiedFileInContent2 = findLastModifiedFileInAlfData("encryptingFileFacadeStore", exclusions);

        Assert.assertNotNull(lastModifiedFileInContent2);
        Assert.assertNotEquals(contentBytes.length, Files.size(lastModifiedFileInContent2));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode2.getId())));

        Assert.assertFalse(contentMatches(lastModifiedFileInContent1, lastModifiedFileInContent2));
        // overall length after encrypting must still be the same due to same algorithm / block size
        Assert.assertEquals(Files.size(lastModifiedFileInContent1), Files.size(lastModifiedFileInContent2));
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

        Assert.assertEquals(3, activeKeyList.size());
        Assert.assertEquals(1, inactiveKeyList.size());
        Assert.assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-effs:effs")));
        Assert.assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-aesks:effs-aes ")));
        Assert.assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-aesks:effs-aes2")));
        Assert.assertTrue(inactiveKeyList.contains("No keys found"));

        final String documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "encrypting-file-facade",
                "Encrypting File Facade Site");

        this.createRandomContents(30, nodes, documentLibraryNodeId);

        final List<String> referenceCounts = commandConsolePlugin
                .countEncryptedSymmetricKeys(CommandConsolePluginRequest.from("scs-aesks:effs-aes2")).getPreformattedOutputLines();
        final String referenceCountLine = referenceCounts.get(0);
        Assert.assertNotEquals("No symmetric keys found", referenceCountLine);
        final int referenceCount = Integer.parseInt(referenceCountLine.substring(0, referenceCountLine.indexOf(' ')));

        commandConsolePlugin.disableEncryptionKey(CommandConsolePluginRequest.from("scs-aesks:effs-aes2"));

        activeKeyList = commandConsolePlugin.listEncryptionKeys(CommandConsolePluginRequest.from("active")).getPreformattedOutputLines();
        inactiveKeyList = commandConsolePlugin.listEncryptionKeys(CommandConsolePluginRequest.from("inactive"))
                .getPreformattedOutputLines();

        Assert.assertEquals(2, activeKeyList.size());
        Assert.assertEquals(1, inactiveKeyList.size());
        Assert.assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-effs:effs")));
        Assert.assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-aesks:effs-aes ")));
        Assert.assertFalse(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-aesks:effs-aes2")));
        Assert.assertTrue(inactiveKeyList.stream().anyMatch(line -> line.startsWith("scs-aesks:effs-aes2")));

        this.createRandomContents(30, nodes, documentLibraryNodeId);

        final List<String> updatedCounts = commandConsolePlugin
                .countEncryptedSymmetricKeys(CommandConsolePluginRequest.from("scs-aesks:effs-aes2")).getPreformattedOutputLines();
        final String updatedCountLine = updatedCounts.get(0);
        final int updatedCount = Integer.parseInt(updatedCountLine.substring(0, updatedCountLine.indexOf(' ')));

        Assert.assertEquals(referenceCount, updatedCount);

        commandConsolePlugin.enableEncryptionKey(CommandConsolePluginRequest.from("scs-aesks:effs-aes2"));

        activeKeyList = commandConsolePlugin.listEncryptionKeys(CommandConsolePluginRequest.from("active")).getPreformattedOutputLines();
        inactiveKeyList = commandConsolePlugin.listEncryptionKeys(CommandConsolePluginRequest.from("inactive"))
                .getPreformattedOutputLines();

        Assert.assertEquals(3, activeKeyList.size());
        Assert.assertEquals(1, inactiveKeyList.size());
        Assert.assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-effs:effs")));
        Assert.assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-aesks:effs-aes ")));
        Assert.assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-aesks:effs-aes2")));
        Assert.assertTrue(inactiveKeyList.contains("No keys found"));

        this.createRandomContents(30, nodes, documentLibraryNodeId);

        final List<String> finalCounts = commandConsolePlugin
                .countEncryptedSymmetricKeys(CommandConsolePluginRequest.from("scs-aesks:effs-aes2")).getPreformattedOutputLines();
        final String finalCountLine = finalCounts.get(0);
        final int finalCount = Integer.parseInt(finalCountLine.substring(0, finalCountLine.indexOf(' ')));

        Assert.assertNotEquals(referenceCount, finalCount);
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

        Assert.assertEquals(3, activeKeyList.size());
        Assert.assertEquals(1, inactiveKeyList.size());
        Assert.assertEquals(1, eligibleKeyList.size());
        Assert.assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-effs:effs")));
        Assert.assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-aesks:effs-aes ")));
        Assert.assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-aesks:effs-aes2")));
        Assert.assertTrue(inactiveKeyList.contains("No keys found"));
        Assert.assertTrue(eligibleKeyList.contains("No keys found"));

        final String documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "encrypting-file-facade",
                "Encrypting File Facade Site");

        this.createRandomContents(30, nodes, documentLibraryNodeId);

        final List<String> referenceCounts = commandConsolePlugin
                .countEncryptedSymmetricKeys(CommandConsolePluginRequest.from("scs-aesks:effs-aes2")).getPreformattedOutputLines();
        final String referenceCountLine = referenceCounts.get(0);
        Assert.assertNotEquals("No symmetric keys found", referenceCountLine);

        commandConsolePlugin.disableEncryptionKey(CommandConsolePluginRequest.from("scs-aesks:effs-aes2"));

        activeKeyList = commandConsolePlugin.listEncryptionKeys(CommandConsolePluginRequest.from("active")).getPreformattedOutputLines();
        inactiveKeyList = commandConsolePlugin.listEncryptionKeys(CommandConsolePluginRequest.from("inactive"))
                .getPreformattedOutputLines();
        eligibleKeyList = commandConsolePlugin.listEncryptionKeysEligibleForReEncryption(CommandConsolePluginRequest.from())
                .getPreformattedOutputLines();

        Assert.assertEquals(2, activeKeyList.size());
        Assert.assertEquals(1, inactiveKeyList.size());
        Assert.assertEquals(1, eligibleKeyList.size());
        Assert.assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-effs:effs ")));
        Assert.assertTrue(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-aesks:effs-aes ")));
        Assert.assertFalse(activeKeyList.stream().anyMatch(line -> line.startsWith("scs-aesks:effs-aes2 ")));
        Assert.assertTrue(inactiveKeyList.stream().anyMatch(line -> line.startsWith("scs-aesks:effs-aes2 ")));
        Assert.assertTrue(eligibleKeyList.stream().anyMatch(line -> line.startsWith("scs-aesks:effs-aes2 ")));

        commandConsolePlugin.reEncryptSymmetricKeys(CommandConsolePluginRequest.from("scs-aesks:effs-aes2"));

        final List<String> updatedCounts = commandConsolePlugin
                .countEncryptedSymmetricKeys(CommandConsolePluginRequest.from("scs-aesks:effs-aes2")).getPreformattedOutputLines();
        final String updatedCountLine = updatedCounts.get(0);
        Assert.assertEquals("No symmetric keys found", updatedCountLine);
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
