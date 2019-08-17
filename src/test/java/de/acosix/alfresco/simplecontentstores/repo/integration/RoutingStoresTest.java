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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.UUID;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.thedeanda.lorem.LoremIpsum;

import de.acosix.alfresco.rest.client.api.NodesV1;
import de.acosix.alfresco.rest.client.api.PeopleV1;
import de.acosix.alfresco.rest.client.model.nodes.NodeCopyMoveRequestEntity;
import de.acosix.alfresco.rest.client.model.nodes.NodeCreationRequestEntity;
import de.acosix.alfresco.rest.client.model.nodes.NodeResponseEntity;
import de.acosix.alfresco.rest.client.model.people.PersonRequestEntity;

/**
 *
 * @author Axel Faust
 */
public class RoutingStoresTest extends AbstractStoresTest
{

    private static ResteasyClient client;

    private static String testUser;

    private static String testUserPassword;

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
    public void fallbackRoutedToDefaultFileStore() throws Exception
    {
        // need to record pre-existing files to exclude in verification
        final Collection<Path> exclusions = listFilesInAlfData("contentstore");

        final String ticket = obtainTicket(client, baseUrl, testUser, testUserPassword);
        final NodesV1 nodes = createAPI(client, baseUrl, NodesV1.class, ticket);

        final NodeCreationRequestEntity createRequest = new NodeCreationRequestEntity();
        createRequest.setName(UUID.randomUUID().toString() + ".txt");
        createRequest.setNodeType("cm:content");

        // test fallback for repository content
        final NodeResponseEntity createdNode = nodes.createNode("-shared-", createRequest);

        final byte[] contentBytes = LoremIpsum.getInstance().getParagraphs(1, 10).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        final Path lastModifiedFileInContent = findLastModifiedFileInAlfData("contentstore", exclusions);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));
    }

    @Test
    public void siteRoutedToSiteRoutingFileStore_uniqueStores() throws Exception
    {
        // need to record pre-existing files to exclude in verification
        final Collection<Path> exclusionsSpecificSite1 = listFilesInAlfData("siteRoutingFileStore1/site-1");
        final Collection<Path> exclusionsSpecificSite2 = listFilesInAlfData("siteRoutingFileStore1/site-2");
        final Collection<Path> exclusionsGenericSite1 = listFilesInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-1");
        final Collection<Path> exclusionsGenericSite2 = listFilesInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-2");

        final String ticket = obtainTicket(client, baseUrl, testUser, testUserPassword);
        final NodesV1 nodes = createAPI(client, baseUrl, NodesV1.class, ticket);

        // 1) check routing for 1st explicit site
        String documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "explicitly-routed-site-1",
                "Explicit Site 1");

        final NodeCreationRequestEntity createRequest = new NodeCreationRequestEntity();
        createRequest.setName(UUID.randomUUID().toString() + ".txt");
        createRequest.setNodeType("cm:content");

        NodeResponseEntity createdNode = nodes.createNode(documentLibraryNodeId, createRequest);

        final byte[] contentBytes = LoremIpsum.getInstance().getParagraphs(1, 10).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        Path lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/site-1", exclusionsSpecificSite1);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 2) verify routing for 2nd explicit site
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "explicitly-routed-site-2", "Explicit Site 2");

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);
        // can use same content as we are looking for difference in paths
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/site-2", exclusionsSpecificSite2);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 3) check generic filing for non-explicitly configured sites
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "genericly-routed-site-1", "Generic Site 1");

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);
        // can use same content as we are looking for difference in paths
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-1",
                exclusionsGenericSite1);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 4) verify generic filing for 2nd non-explicitly configured sites
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "genericly-routed-site-2", "Generic Site 2");

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);
        // can use same content as we are looking for difference in paths
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-2",
                exclusionsGenericSite2);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));
    }

    @Test
    public void siteRoutedToSiteRoutingFileStore_sharedStores() throws Exception
    {
        // need to record pre-existing files to exclude in verification
        final Collection<Path> exclusionsSpecificSites = listFilesInAlfData("siteRoutingFileStore2/sharedExplicitSites");
        final Collection<Path> exclusionsGenericSites = listFilesInAlfData("siteRoutingFileStore2/sharedGenericSites");

        final String ticket = obtainTicket(client, baseUrl, testUser, testUserPassword);
        final NodesV1 nodes = createAPI(client, baseUrl, NodesV1.class, ticket);

        // 1) check routing for 1st explicit site
        String documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "explicitly-routed-site-3",
                "Explicit Site 3");

        final NodeCreationRequestEntity createRequest = new NodeCreationRequestEntity();
        createRequest.setName(UUID.randomUUID().toString() + ".txt");
        createRequest.setNodeType("cm:content");

        NodeResponseEntity createdNode = nodes.createNode(documentLibraryNodeId, createRequest);

        final byte[] contentBytes = LoremIpsum.getInstance().getParagraphs(1, 10).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        Path lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore2/sharedExplicitSites",
                exclusionsSpecificSites);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 2) verify routing for 2nd explicit site
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "explicitly-routed-site-4", "Explicit Site 4");

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);
        // can use same content as we are looking for difference in paths
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        exclusionsSpecificSites.add(lastModifiedFileInContent);
        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore2/sharedExplicitSites", exclusionsSpecificSites);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 3) check generic filing for non-explicitly configured sites
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "genericly-routed-site-3", "Generic Site 3");

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);
        // can use same content as we are looking for difference in paths
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore2/sharedGenericSites", exclusionsGenericSites);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 4) verify generic filing for 2nd non-explicitly configured sites
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "genericly-routed-site-4", "Generic Site 4");

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);
        // can use same content as we are looking for difference in paths
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        exclusionsGenericSites.add(lastModifiedFileInContent);
        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore2/sharedGenericSites", exclusionsGenericSites);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));
    }

    @Test
    public void siteRoutedToSiteRoutingFileStore_copyToSiteWithDifferentStoreWithOnCopyMoveHandling() throws Exception
    {
        // need to record pre-existing files to exclude in verification
        final Collection<Path> exclusionsSpecificSite1 = listFilesInAlfData("siteRoutingFileStore1/site-1");
        final Collection<Path> exclusionsSpecificSite2 = listFilesInAlfData("siteRoutingFileStore1/site-2");
        final Collection<Path> exclusionsGenericSite1 = listFilesInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-1");
        final Collection<Path> exclusionsGenericSite2 = listFilesInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-2");

        final String ticket = obtainTicket(client, baseUrl, testUser, testUserPassword);
        final NodesV1 nodes = createAPI(client, baseUrl, NodesV1.class, ticket);

        // 1) check routing for 1st explicit site
        String documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "explicitly-routed-site-1",
                "Explicit Site 1");

        final NodeCreationRequestEntity createRequest = new NodeCreationRequestEntity();
        createRequest.setName(UUID.randomUUID().toString() + ".txt");
        createRequest.setNodeType("cm:content");

        NodeResponseEntity createdNode = nodes.createNode(documentLibraryNodeId, createRequest);

        final byte[] contentBytes = LoremIpsum.getInstance().getParagraphs(1, 10).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        Path lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/site-1", exclusionsSpecificSite1);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 2) copy to 2nd explicit site and verify new, identical content was created
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "explicitly-routed-site-2", "Explicit Site 2");

        final NodeCopyMoveRequestEntity copyRq = new NodeCopyMoveRequestEntity();
        copyRq.setTargetParentId(documentLibraryNodeId);

        NodeResponseEntity copyNode = nodes.copyNode(createdNode.getId(), copyRq);

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/site-2", exclusionsSpecificSite2);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(copyNode.getId())));

        // 3) check generic filing for non-explicitly configured sites
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "genericly-routed-site-1", "Generic Site 1");

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);
        // can use same content as we are looking for difference in paths
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-1",
                exclusionsGenericSite1);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 2) copy to 2nd generic site and verify new, identical content was created
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "genericly-routed-site-2", "Generic Site 2");

        copyRq.setTargetParentId(documentLibraryNodeId);

        copyNode = nodes.copyNode(createdNode.getId(), copyRq);

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-2",
                exclusionsGenericSite2);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(copyNode.getId())));
    }

    @Test
    public void siteRoutedToSiteRoutingFileStore_copyToSiteWithDifferentStoreWithoutOnCopyMoveHandling() throws Exception
    {
        // need to record pre-existing files to exclude in verification
        final Collection<Path> exclusionsSpecificSites = listFilesInAlfData("siteRoutingFileStore2/sharedExplicitSites");
        final Collection<Path> exclusionsGenericSites = listFilesInAlfData("siteRoutingFileStore2/sharedGenericSites");

        final String ticket = obtainTicket(client, baseUrl, testUser, testUserPassword);
        final NodesV1 nodes = createAPI(client, baseUrl, NodesV1.class, ticket);

        // 1) check routing for 1st explicit site
        String documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "explicitly-routed-site-3",
                "Explicit Site 3");

        final NodeCreationRequestEntity createRequest = new NodeCreationRequestEntity();
        createRequest.setName(UUID.randomUUID().toString() + ".txt");
        createRequest.setNodeType("cm:content");

        final NodeResponseEntity createdNode = nodes.createNode(documentLibraryNodeId, createRequest);

        final byte[] contentBytes = LoremIpsum.getInstance().getParagraphs(1, 10).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        Path lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore2/sharedExplicitSites",
                exclusionsSpecificSites);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 2) copy to generic site and verify no new content was created
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "genericly-routed-site-3", "Generic Site 3");

        final NodeCopyMoveRequestEntity copyRq = new NodeCopyMoveRequestEntity();
        copyRq.setTargetParentId(documentLibraryNodeId);

        final NodeResponseEntity copyNode = nodes.copyNode(createdNode.getId(), copyRq);

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore2/sharedGenericSites", exclusionsGenericSites);

        Assert.assertNull(lastModifiedFileInContent);
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(copyNode.getId())));
    }

    @Test
    public void siteRoutedToSiteRoutingFileStore_copyToSiteWithSameStore() throws Exception
    {
        // need to record pre-existing files to exclude in verification
        final Collection<Path> exclusionsSpecificSite1 = listFilesInAlfData("siteRoutingFileStore1/site-1");
        final Collection<Path> exclusionsGenericSite1 = listFilesInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-1");

        final String ticket = obtainTicket(client, baseUrl, testUser, testUserPassword);
        final NodesV1 nodes = createAPI(client, baseUrl, NodesV1.class, ticket);

        // 1) check routing for 1st explicit site
        String documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "explicitly-routed-site-1",
                "Explicit Site 1");

        final NodeCreationRequestEntity createRequest = new NodeCreationRequestEntity();
        createRequest.setName(UUID.randomUUID().toString() + ".txt");
        createRequest.setNodeType("cm:content");

        NodeResponseEntity createdNode = nodes.createNode(documentLibraryNodeId, createRequest);

        final byte[] contentBytes = LoremIpsum.getInstance().getParagraphs(1, 10).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        Path lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/site-1", exclusionsSpecificSite1);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 2) copy to same site and verify no new content file was created
        final NodeCopyMoveRequestEntity copyRq = new NodeCopyMoveRequestEntity();
        copyRq.setTargetParentId(documentLibraryNodeId);
        copyRq.setName("Copy of " + createdNode.getName());

        NodeResponseEntity copyNode = nodes.copyNode(createdNode.getId(), copyRq);

        exclusionsSpecificSite1.add(lastModifiedFileInContent);
        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/site-1", exclusionsSpecificSite1);

        Assert.assertNull(lastModifiedFileInContent);
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(copyNode.getId())));

        // 3) check generic filing for non-explicitly configured sites
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "genericly-routed-site-1", "Generic Site 1");

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);
        // can use same content as we are looking for difference in paths
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-1",
                exclusionsGenericSite1);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 4) copy to same site and verify no new content file was created
        copyRq.setTargetParentId(documentLibraryNodeId);
        copyRq.setName("Copy of " + createdNode.getName());

        copyNode = nodes.copyNode(createdNode.getId(), copyRq);

        exclusionsGenericSite1.add(lastModifiedFileInContent);
        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-1",
                exclusionsGenericSite1);

        Assert.assertNull(lastModifiedFileInContent);
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(copyNode.getId())));
    }

    @Test
    public void siteRoutedToSiteRoutingFileStore_moveToSiteWithDifferentStoreWithOnCopyMoveHandling() throws Exception
    {
        // need to record pre-existing files to exclude in verification
        final Collection<Path> exclusionsSpecificSite1 = listFilesInAlfData("siteRoutingFileStore1/site-1");
        final Collection<Path> exclusionsSpecificSite2 = listFilesInAlfData("siteRoutingFileStore1/site-2");
        final Collection<Path> exclusionsGenericSite1 = listFilesInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-1");
        final Collection<Path> exclusionsGenericSite2 = listFilesInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-2");

        final String ticket = obtainTicket(client, baseUrl, testUser, testUserPassword);
        final NodesV1 nodes = createAPI(client, baseUrl, NodesV1.class, ticket);

        // 1) check routing for 1st explicit site
        String documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "explicitly-routed-site-1",
                "Explicit Site 1");

        final NodeCreationRequestEntity createRequest = new NodeCreationRequestEntity();
        createRequest.setName(UUID.randomUUID().toString() + ".txt");
        createRequest.setNodeType("cm:content");

        NodeResponseEntity createdNode = nodes.createNode(documentLibraryNodeId, createRequest);

        final byte[] contentBytes = LoremIpsum.getInstance().getParagraphs(1, 10).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        Path lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/site-1", exclusionsSpecificSite1);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 2) move to 2nd explicit site and verify new content was created + previous (orphaned) content eager deleted
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "explicitly-routed-site-2", "Explicit Site 2");

        final NodeCopyMoveRequestEntity moveRq = new NodeCopyMoveRequestEntity();
        moveRq.setTargetParentId(documentLibraryNodeId);

        nodes.moveNode(createdNode.getId(), moveRq);

        Assert.assertFalse(Files.exists(lastModifiedFileInContent));

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/site-2", exclusionsSpecificSite2);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 3) check generic filing for non-explicitly configured sites
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "genericly-routed-site-1", "Generic Site 1");

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);
        // can use same content as we are looking for difference in paths
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-1",
                exclusionsGenericSite1);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 2) move to 2nd generic site and verify new content was created + previous (orphaned) content eager deleted
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "genericly-routed-site-2", "Generic Site 2");

        moveRq.setTargetParentId(documentLibraryNodeId);

        nodes.moveNode(createdNode.getId(), moveRq);

        Assert.assertFalse(Files.exists(lastModifiedFileInContent));

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-2",
                exclusionsGenericSite2);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));
    }

    @Test
    public void siteRoutedToSiteRoutingFileStore_moveToSiteWithDifferentStoreWithoutOnCopyMoveHandling() throws Exception
    {
        // need to record pre-existing files to exclude in verification
        final Collection<Path> exclusionsSpecificSites = listFilesInAlfData("siteRoutingFileStore2/sharedExplicitSites");
        final Collection<Path> exclusionsGenericSites = listFilesInAlfData("siteRoutingFileStore2/sharedGenericSites");

        final String ticket = obtainTicket(client, baseUrl, testUser, testUserPassword);
        final NodesV1 nodes = createAPI(client, baseUrl, NodesV1.class, ticket);

        // 1) check routing for 1st explicit site
        String documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "explicitly-routed-site-3",
                "Explicit Site 3");

        final NodeCreationRequestEntity createRequest = new NodeCreationRequestEntity();
        createRequest.setName(UUID.randomUUID().toString() + ".txt");
        createRequest.setNodeType("cm:content");

        final NodeResponseEntity createdNode = nodes.createNode(documentLibraryNodeId, createRequest);

        final byte[] contentBytes = LoremIpsum.getInstance().getParagraphs(1, 10).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        Path lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore2/sharedExplicitSites",
                exclusionsSpecificSites);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 2) move to generic site and verify no new content was created while old still exists
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "genericly-routed-site-3", "Generic Site 3");

        final NodeCopyMoveRequestEntity moveRq = new NodeCopyMoveRequestEntity();
        moveRq.setTargetParentId(documentLibraryNodeId);

        nodes.moveNode(createdNode.getId(), moveRq);

        Assert.assertTrue(Files.exists(lastModifiedFileInContent));

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore2/sharedGenericSites", exclusionsGenericSites);

        Assert.assertNull(lastModifiedFileInContent);

        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));
    }

    // TODO Tests for other routing store configurations
}
