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
package de.acosix.alfresco.simplecontentstores.repo.integration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.thedeanda.lorem.LoremIpsum;

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
public class TransformingFacadeStoresTest extends AbstractStoresTest
{

    private static ResteasyClient client;

    private static String testUser;

    private static String testUserPassword;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @BeforeClass
    public static void setup()
    {
        client = setupResteasyClient();

        final String ticket = obtainTicket(client, baseUrl, "admin", "admin");
        final PeopleV1 people = createAPI(client, baseUrl, PeopleV1.class, ticket);

        final PersonRequestEntity personToCreate = new PersonRequestEntity();
        testUser = UUID.randomUUID().toString();
        personToCreate.setEmail(testUser + "@example.com");
        personToCreate.setFirstName("Test");
        personToCreate.setLastName("Guy");
        personToCreate.setId(testUser);
        testUserPassword = UUID.randomUUID().toString();
        personToCreate.setPassword(testUserPassword);
        people.createPerson(personToCreate);
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
    public void encryptingFacadeStore() throws IOException
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
        nodes.setContent(createdNode2.getId(), new ByteArrayInputStream(contentBytes), "text/html");

        exclusions.add(lastModifiedFileInContent1);
        final Path lastModifiedFileInContent2 = findLastModifiedFileInAlfData("encryptingFileFacadeStore", exclusions);

        Assert.assertNotNull(lastModifiedFileInContent2);
        Assert.assertNotEquals(contentBytes.length, Files.size(lastModifiedFileInContent2));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode2.getId())));

        Assert.assertFalse(contentMatches(lastModifiedFileInContent1, lastModifiedFileInContent2));
        // overall length after encrypting must still be the same due to same algorithm / block size
        Assert.assertEquals(Files.size(lastModifiedFileInContent1), Files.size(lastModifiedFileInContent2));
    }
}
