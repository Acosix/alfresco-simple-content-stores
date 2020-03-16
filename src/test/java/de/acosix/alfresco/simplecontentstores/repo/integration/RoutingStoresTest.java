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
import de.acosix.alfresco.rest.client.model.nodes.CommonNodeEntity;
import de.acosix.alfresco.rest.client.model.nodes.NodeCopyMoveRequestEntity;
import de.acosix.alfresco.rest.client.model.nodes.NodeCreationRequestEntity;
import de.acosix.alfresco.rest.client.model.nodes.NodeResponseEntity;
import de.acosix.alfresco.rest.client.model.nodes.PermissionsInfo;
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
    public void siteRoutingFileStore_uniqueStores() throws Exception
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
    public void siteRoutingFileStore_sharedStores() throws Exception
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
    public void siteRoutingFileStore_copyToSiteWithDifferentStoreWithOnCopyMoveHandling() throws Exception
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
    public void siteRoutingFileStore_copyToSiteWithDifferentStoreWithoutOnCopyMoveHandling() throws Exception
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
    public void siteRoutingFileStore_copyToSiteWithSameStore() throws Exception
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
    public void siteRoutingFileStore_moveToSiteWithDifferentStoreWithOnCopyMoveHandling() throws Exception
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
    public void siteRoutingFileStore_moveToSiteWithDifferentStoreWithoutOnCopyMoveHandling() throws Exception
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

    @Test
    public void propertyRoutingStore_moveEnabledStore() throws Exception
    {
        // need to record pre-existing files to exclude in verification
        final Collection<Path> exclusionsFallbackStore = listFilesInAlfData("propertySelectorFallbackFileStore");
        final Collection<Path> exclusionsStore1 = listFilesInAlfData("propertySelectorFileStore11");
        final Collection<Path> exclusionsStore2 = listFilesInAlfData("propertySelectorFileStore12");

        final String ticket = obtainTicket(client, baseUrl, testUser, testUserPassword);
        final NodesV1 nodes = createAPI(client, baseUrl, NodesV1.class, ticket);

        final String documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "property-selector-1",
                "Property Selector Site 1");

        // 1) test fallback routing without property value
        final NodeCreationRequestEntity createRequest = new NodeCreationRequestEntity();
        createRequest.setName(UUID.randomUUID().toString() + ".txt");
        createRequest.setNodeType("cm:content");

        final NodeResponseEntity createdNode = nodes.createNode(documentLibraryNodeId, createRequest);

        byte[] contentBytes = LoremIpsum.getInstance().getParagraphs(1, 10).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        Path lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFallbackFileStore", exclusionsFallbackStore);
        // not adding to exclusions as we later will move it back, resulting in the same file
        // exclusionsFallbackStore.add(lastModifiedFileInContent);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 2) test move when property value is set
        final CommonNodeEntity<PermissionsInfo> nodeUpdate = new CommonNodeEntity<>();
        nodeUpdate.setProperty("aco6scst:selectorProperty", "store1");
        nodes.updateNode(createdNode.getId(), nodeUpdate);

        Assert.assertFalse(Files.exists(lastModifiedFileInContent));

        lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFileStore11", exclusionsStore1);
        exclusionsStore1.add(lastModifiedFileInContent);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 3) test move to fallback when unmapped property value is set
        nodeUpdate.setProperty("aco6scst:selectorProperty", "nonExistingStore");
        nodes.updateNode(createdNode.getId(), nodeUpdate);

        Assert.assertFalse(Files.exists(lastModifiedFileInContent));

        lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFallbackFileStore", exclusionsFallbackStore);
        exclusionsFallbackStore.add(lastModifiedFileInContent);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 4) test move/copy to store via copy + setting of selector property
        final NodeCopyMoveRequestEntity copyRq = new NodeCopyMoveRequestEntity();
        copyRq.setName(UUID.randomUUID().toString() + ".txt");
        copyRq.setTargetParentId(documentLibraryNodeId);

        final NodeResponseEntity copiedNode = nodes.copyNode(createdNode.getId(), copyRq);

        nodeUpdate.setProperty("aco6scst:selectorProperty", "store2");
        nodes.updateNode(copiedNode.getId(), nodeUpdate);

        Assert.assertTrue(Files.exists(lastModifiedFileInContent));

        lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFileStore12", exclusionsStore2);
        exclusionsStore2.add(lastModifiedFileInContent);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 5) test update of copy and deletion of copied content
        contentBytes = LoremIpsum.getInstance().getParagraphs(1, 10).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(copiedNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        Assert.assertFalse(Files.exists(lastModifiedFileInContent));

        lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFileStore12", exclusionsStore2);
        exclusionsStore2.add(lastModifiedFileInContent);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(copiedNode.getId())));

        // 6) test property to prevent move
        nodeUpdate.setProperty("aco6scst:moveStoreOnSelectorChange", "false");
        nodeUpdate.setProperty("aco6scst:selectorProperty", "store1");
        nodes.updateNode(copiedNode.getId(), nodeUpdate);

        Assert.assertTrue(Files.exists(lastModifiedFileInContent));

        lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFileStore11", exclusionsStore1);

        Assert.assertNull(lastModifiedFileInContent);
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(copiedNode.getId())));
    }

    @Test
    public void propertyRoutingStore_moveDisabledStore() throws Exception
    {
        // need to record pre-existing files to exclude in verification
        final Collection<Path> exclusionsStore1 = listFilesInAlfData("propertySelectorFileStore21");
        final Collection<Path> exclusionsStore2 = listFilesInAlfData("propertySelectorFileStore22");

        final String ticket = obtainTicket(client, baseUrl, testUser, testUserPassword);
        final NodesV1 nodes = createAPI(client, baseUrl, NodesV1.class, ticket);

        final String documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "property-selector-2",
                "Property Selector Site 2");

        // 1) test fallback routing without property value
        final NodeCreationRequestEntity createRequest = new NodeCreationRequestEntity();
        createRequest.setName(UUID.randomUUID().toString() + ".txt");
        createRequest.setNodeType("cm:content");
        createRequest.setProperty("aco6scst:selectorProperty", "store2");

        NodeResponseEntity createdNode = nodes.createNode(documentLibraryNodeId, createRequest);

        byte[] contentBytes = LoremIpsum.getInstance().getParagraphs(1, 10).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        Path lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFileStore22", exclusionsStore2);
        exclusionsStore2.add(lastModifiedFileInContent);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 2) test lack of move when property value is set
        final CommonNodeEntity<PermissionsInfo> nodeUpdate = new CommonNodeEntity<>();
        nodeUpdate.setProperty("aco6scst:selectorProperty", "store1");
        nodes.updateNode(createdNode.getId(), nodeUpdate);

        Assert.assertTrue(Files.exists(lastModifiedFileInContent));

        final Path lastModifiedFileInStore22 = lastModifiedFileInContent;
        lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFileStore21", exclusionsStore1);

        Assert.assertNull(lastModifiedFileInContent);

        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 3) test routing to specific store when content is updated
        contentBytes = LoremIpsum.getInstance().getParagraphs(1, 10).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        Assert.assertFalse(Files.exists(lastModifiedFileInStore22));

        lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFileStore21", exclusionsStore1);
        exclusionsStore1.add(lastModifiedFileInContent);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 4) test routing to specific store when property already set during creation
        createRequest.setName(UUID.randomUUID().toString() + ".txt");
        createRequest.setProperty("aco6scst:selectorProperty", "store2");
        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);

        contentBytes = LoremIpsum.getInstance().getParagraphs(1, 10).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFileStore22", exclusionsStore2);
        exclusionsStore2.add(lastModifiedFileInContent);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));
    }

    @Test
    public void typeRoutingStore_moveEnabledStore() throws Exception
    {
        // need to record pre-existing files to exclude in verification
        final Collection<Path> exclusionsFallbackStore = listFilesInAlfData("typeRoutingFallbackFileStore1");
        final Collection<Path> exclusionsStore1 = listFilesInAlfData("typeRoutingFileStore1");

        final String ticket = obtainTicket(client, baseUrl, testUser, testUserPassword);
        final NodesV1 nodes = createAPI(client, baseUrl, NodesV1.class, ticket);

        final String documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "type-routing-1",
                "Type Routing Site 1");

        // 1) test fallback routing with non-mapped type
        NodeCreationRequestEntity createRequest = new NodeCreationRequestEntity();
        createRequest.setName(UUID.randomUUID().toString() + ".txt");
        createRequest.setNodeType("cm:content");

        NodeResponseEntity createdNode = nodes.createNode(documentLibraryNodeId, createRequest);

        byte[] contentBytes = LoremIpsum.getInstance().getParagraphs(1, 10).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        Path lastModifiedFileInContent = findLastModifiedFileInAlfData("typeRoutingFallbackFileStore1", exclusionsFallbackStore);
        exclusionsFallbackStore.add(lastModifiedFileInContent);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 2) test move when type is changed (sub-type of mapped type)
        CommonNodeEntity<PermissionsInfo> nodeUpdate = new CommonNodeEntity<>();
        nodeUpdate.setNodeType("aco6scst:invoiceDocument");
        nodes.updateNode(createdNode.getId(), nodeUpdate);

        Assert.assertFalse(Files.exists(lastModifiedFileInContent));

        lastModifiedFileInContent = findLastModifiedFileInAlfData("typeRoutingFileStore1", exclusionsStore1);
        exclusionsStore1.add(lastModifiedFileInContent);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 3) test direct filing with mapped type
        createRequest = new NodeCreationRequestEntity();
        createRequest.setName(UUID.randomUUID().toString() + ".txt");
        createRequest.setNodeType("aco6scst:archiveDocument");

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);

        contentBytes = LoremIpsum.getInstance().getParagraphs(1, 10).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("typeRoutingFileStore1", exclusionsFallbackStore);
        exclusionsStore1.add(lastModifiedFileInContent);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 4) test property to prevent move
        createRequest = new NodeCreationRequestEntity();
        createRequest.setName(UUID.randomUUID().toString() + ".txt");
        createRequest.setNodeType("cm:content");

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);

        contentBytes = LoremIpsum.getInstance().getParagraphs(1, 10).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("typeRoutingFallbackFileStore1", exclusionsFallbackStore);
        exclusionsFallbackStore.add(lastModifiedFileInContent);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        nodeUpdate = new CommonNodeEntity<>();
        nodeUpdate.setProperty("aco6scst:moveStoreOnSelectorChange", "false");
        nodes.updateNode(createdNode.getId(), nodeUpdate);

        nodeUpdate = new CommonNodeEntity<>();
        nodeUpdate.setNodeType("aco6scst:invoiceDocument");
        nodes.updateNode(createdNode.getId(), nodeUpdate);

        Assert.assertTrue(Files.exists(lastModifiedFileInContent));

        lastModifiedFileInContent = findLastModifiedFileInAlfData("typeRoutingFileStore1", exclusionsStore1);

        Assert.assertNull(lastModifiedFileInContent);
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));
    }

    @Test
    public void typeRoutingStore_moveDisabledStore() throws Exception
    {
        // need to record pre-existing files to exclude in verification
        final Collection<Path> exclusionsFallbackStore = listFilesInAlfData("typeRoutingFallbackFileStore2");
        final Collection<Path> exclusionsStore2 = listFilesInAlfData("typeRoutingFileStore2");

        final String ticket = obtainTicket(client, baseUrl, testUser, testUserPassword);
        final NodesV1 nodes = createAPI(client, baseUrl, NodesV1.class, ticket);

        final String documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "type-routing-2",
                "Type Routing Site 2");

        // 1) test fallback routing with non-mapped type
        NodeCreationRequestEntity createRequest = new NodeCreationRequestEntity();
        createRequest.setName(UUID.randomUUID().toString() + ".txt");
        createRequest.setNodeType("cm:content");

        NodeResponseEntity createdNode = nodes.createNode(documentLibraryNodeId, createRequest);

        byte[] contentBytes = LoremIpsum.getInstance().getParagraphs(1, 10).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        Path lastModifiedFileInContent = findLastModifiedFileInAlfData("typeRoutingFallbackFileStore2", exclusionsFallbackStore);
        exclusionsFallbackStore.add(lastModifiedFileInContent);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 2) test non-move when type is changed
        final CommonNodeEntity<PermissionsInfo> nodeUpdate = new CommonNodeEntity<>();
        nodeUpdate.setNodeType("aco6scst:archiveDocument");
        nodes.updateNode(createdNode.getId(), nodeUpdate);

        Assert.assertTrue(Files.exists(lastModifiedFileInContent));

        lastModifiedFileInContent = findLastModifiedFileInAlfData("typeRoutingFileStore2", exclusionsStore2);

        Assert.assertNull(lastModifiedFileInContent);
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 3) test direct filing with sub-type of mapped type
        createRequest = new NodeCreationRequestEntity();
        createRequest.setName(UUID.randomUUID().toString() + ".txt");
        createRequest.setNodeType("aco6scst:invoiceDocument");

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);

        contentBytes = LoremIpsum.getInstance().getParagraphs(1, 10).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("typeRoutingFileStore2", exclusionsFallbackStore);
        exclusionsStore2.add(lastModifiedFileInContent);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));
    }

    @Test
    public void siteRoutingStore_copyMoveBetweenSitesWithSharedFileStore() throws Exception
    {
        final Collection<Path> exclusionsFallbackStore = listFilesInAlfData("propertySelectorFallbackFileStore");

        final String ticket = obtainTicket(client, baseUrl, testUser, testUserPassword);
        final NodesV1 nodes = createAPI(client, baseUrl, NodesV1.class, ticket);

        final String documentLibrary1NodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "property-selector-1",
                "Property Selector Site 1");
        final String documentLibrary2NodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "property-selector-2",
                "Property Selector Site 2");

        final NodeCreationRequestEntity createRequest = new NodeCreationRequestEntity();
        createRequest.setName(UUID.randomUUID().toString() + ".txt");
        createRequest.setNodeType("cm:content");

        NodeResponseEntity createdNode = nodes.createNode(documentLibrary1NodeId, createRequest);

        byte[] contentBytes = LoremIpsum.getInstance().getParagraphs(1, 10).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        Path lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFallbackFileStore", exclusionsFallbackStore);
        exclusionsFallbackStore.add(lastModifiedFileInContent);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // test move across sites with same fallback store (selector property is not set)
        final NodeCopyMoveRequestEntity moveCopyRq = new NodeCopyMoveRequestEntity();
        moveCopyRq.setTargetParentId(documentLibrary2NodeId);

        nodes.moveNode(createdNode.getId(), moveCopyRq);

        Assert.assertTrue(Files.exists(lastModifiedFileInContent));

        lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFallbackFileStore", exclusionsFallbackStore);

        Assert.assertNull(lastModifiedFileInContent);
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // create second test content
        createRequest.setName(UUID.randomUUID().toString() + ".txt");
        createdNode = nodes.createNode(documentLibrary1NodeId, createRequest);

        contentBytes = LoremIpsum.getInstance().getParagraphs(1, 10).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFallbackFileStore", exclusionsFallbackStore);
        exclusionsFallbackStore.add(lastModifiedFileInContent);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // test copy across sites with same fallback store (selector property is not set)
        final NodeResponseEntity copiedNode = nodes.copyNode(createdNode.getId(), moveCopyRq);

        Assert.assertTrue(Files.exists(lastModifiedFileInContent));

        final Path sharedFileInContent = lastModifiedFileInContent;
        lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFallbackFileStore", exclusionsFallbackStore);

        Assert.assertNull(lastModifiedFileInContent);
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(copiedNode.getId())));

        // test that shared content is not deleted if either of the copies is deleted
        nodes.deleteNode(createdNode.getId());

        Assert.assertTrue(Files.exists(sharedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(copiedNode.getId())));
    }

    @Test
    public void siteRoutingStore_copyMoveBetweenSitesWithDistinctFileStore() throws Exception
    {
        final Collection<Path> exclusionsStore11 = listFilesInAlfData("propertySelectorFileStore11");
        final Collection<Path> exclusionsStore21 = listFilesInAlfData("propertySelectorFileStore21");

        final String ticket = obtainTicket(client, baseUrl, testUser, testUserPassword);
        final NodesV1 nodes = createAPI(client, baseUrl, NodesV1.class, ticket);

        final String documentLibrary1NodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "property-selector-1",
                "Property Selector Site 1");
        final String documentLibrary2NodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "property-selector-2",
                "Property Selector Site 2");

        final NodeCreationRequestEntity createRequest = new NodeCreationRequestEntity();
        createRequest.setName(UUID.randomUUID().toString() + ".txt");
        createRequest.setNodeType("cm:content");
        createRequest.setProperty("aco6scst:selectorProperty", "store1");

        NodeResponseEntity createdNode = nodes.createNode(documentLibrary1NodeId, createRequest);

        byte[] contentBytes = LoremIpsum.getInstance().getParagraphs(1, 10).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        Path lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFileStore11", exclusionsStore11);
        exclusionsStore11.add(lastModifiedFileInContent);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // test move across sites with different store (selector property is set)
        final NodeCopyMoveRequestEntity moveCopyRq = new NodeCopyMoveRequestEntity();
        moveCopyRq.setTargetParentId(documentLibrary2NodeId);

        nodes.moveNode(createdNode.getId(), moveCopyRq);

        Assert.assertFalse(Files.exists(lastModifiedFileInContent));

        lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFileStore21", exclusionsStore21);
        exclusionsStore21.add(lastModifiedFileInContent);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // create second test content
        createRequest.setName(UUID.randomUUID().toString());
        createdNode = nodes.createNode(documentLibrary1NodeId, createRequest);

        contentBytes = LoremIpsum.getInstance().getParagraphs(1, 10).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFileStore11", exclusionsStore11);
        exclusionsStore11.add(lastModifiedFileInContent);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // test copy across sites with different store (selector property is set)
        final NodeResponseEntity copiedNode = nodes.copyNode(createdNode.getId(), moveCopyRq);

        Assert.assertTrue(Files.exists(lastModifiedFileInContent));
        final Path lastModifiedFileInContentStore11 = lastModifiedFileInContent;

        lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFileStore21", exclusionsStore21);
        exclusionsStore21.add(lastModifiedFileInContent);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(copiedNode.getId())));

        // test that specific content is deleted if either of the copies is deleted
        nodes.deleteNode(createdNode.getId(), true);

        Assert.assertFalse(Files.exists(lastModifiedFileInContentStore11));
        Assert.assertTrue(Files.exists(lastModifiedFileInContent));
        Assert.assertTrue(contentMatches(contentBytes, nodes.getContent(copiedNode.getId())));
    }
}
