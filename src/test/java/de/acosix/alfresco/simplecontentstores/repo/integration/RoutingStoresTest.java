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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.thedeanda.lorem.LoremIpsum;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.UUID;

import javax.ws.rs.NotFoundException;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.junit.BeforeClass;
import org.junit.Test;

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
    public void fallbackRoutedToDefaultFileStore() throws Exception
    {
        // need to record pre-existing files to exclude in verification
        final Collection<ContentFile> knownFiles = listFilesInAlfData("contentstore");

        final String ticket = obtainTicket(client, baseUrl, testUser, testUserPassword);
        final NodesV1 nodes = createAPI(client, baseUrl, NodesV1.class, ticket);

        final NodeCreationRequestEntity createRequest = new NodeCreationRequestEntity();
        createRequest.setName(UUID.randomUUID().toString() + ".txt");
        createRequest.setNodeType("cm:content");

        // test fallback for repository content
        final NodeResponseEntity createdNode = nodes.createNode("-shared-", createRequest);

        final byte[] contentBytes = LoremIpsum.getInstance().getParagraphs(1, 10).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        final ContentFile lastModifiedFileInContent = findLastModifiedFileInAlfData("contentstore", knownFiles);

        assertNotNull(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));
    }

    @Test
    public void siteRoutingFileStore_uniqueStores() throws Exception
    {
        // need to record pre-existing files to exclude in verification
        final Collection<ContentFile> knownFilesSpecificSite1 = listFilesInAlfData("siteRoutingFileStore1/site-1");
        final Collection<ContentFile> knownFilesSpecificSite2 = listFilesInAlfData("siteRoutingFileStore1/site-2");
        final Collection<ContentFile> knownFilesGenericSite1 = listFilesInAlfData(
                "siteRoutingFileStore1/.otherSites/genericly-routed-site-1");
        final Collection<ContentFile> knownFilesGenericSite2 = listFilesInAlfData(
                "siteRoutingFileStore1/.otherSites/genericly-routed-site-2");

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

        ContentFile lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/site-1", knownFilesSpecificSite1);

        assertNotNull(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 2) verify routing for 2nd explicit site
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "explicitly-routed-site-2", "Explicit Site 2");

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);
        // can use same content as we are looking for difference in paths
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/site-2", knownFilesSpecificSite2);

        assertNotNull(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 3) check generic filing for non-explicitly configured sites
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "genericly-routed-site-1", "Generic Site 1");

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);
        // can use same content as we are looking for difference in paths
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-1",
                knownFilesGenericSite1);

        assertNotNull(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 4) verify generic filing for 2nd non-explicitly configured sites
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "genericly-routed-site-2", "Generic Site 2");

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);
        // can use same content as we are looking for difference in paths
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-2",
                knownFilesGenericSite2);

        assertNotNull(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));
    }

    @Test
    public void siteRoutingFileStore_sharedStores() throws Exception
    {
        // need to record pre-existing files to exclude in verification
        final Collection<ContentFile> knownFilesSpecificSites = listFilesInAlfData("siteRoutingFileStore2/sharedExplicitSites");
        final Collection<ContentFile> knownFilesGenericSites = listFilesInAlfData("siteRoutingFileStore2/sharedGenericSites");

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

        ContentFile lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore2/sharedExplicitSites",
                knownFilesSpecificSites);

        assertNotNull(lastModifiedFileInContent);
        knownFilesSpecificSites.add(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 2) verify routing for 2nd explicit site
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "explicitly-routed-site-4", "Explicit Site 4");

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);
        // can use same content as we are looking for difference in paths
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore2/sharedExplicitSites", knownFilesSpecificSites);

        assertNotNull(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 3) check generic filing for non-explicitly configured sites
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "genericly-routed-site-3", "Generic Site 3");

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);
        // can use same content as we are looking for difference in paths
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore2/sharedGenericSites", knownFilesGenericSites);

        assertNotNull(lastModifiedFileInContent);
        knownFilesGenericSites.add(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 4) verify generic filing for 2nd non-explicitly configured sites
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "genericly-routed-site-4", "Generic Site 4");

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);
        // can use same content as we are looking for difference in paths
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore2/sharedGenericSites", knownFilesGenericSites);

        assertNotNull(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));
    }

    @Test
    public void siteRoutingFileStore_copyToSiteWithDifferentStoreWithOnCopyMoveHandling() throws Exception
    {
        // need to record pre-existing files to exclude in verification
        final Collection<ContentFile> knownFilesSpecificSite1 = listFilesInAlfData("siteRoutingFileStore1/site-1");
        final Collection<ContentFile> knownFilesSpecificSite2 = listFilesInAlfData("siteRoutingFileStore1/site-2");
        final Collection<ContentFile> knownFilesGenericSite1 = listFilesInAlfData(
                "siteRoutingFileStore1/.otherSites/genericly-routed-site-1");
        final Collection<ContentFile> knownFilesGenericSite2 = listFilesInAlfData(
                "siteRoutingFileStore1/.otherSites/genericly-routed-site-2");

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

        ContentFile lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/site-1", knownFilesSpecificSite1);

        assertNotNull(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 2) copy to 2nd explicit site and verify new, identical content was created
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "explicitly-routed-site-2", "Explicit Site 2");

        final NodeCopyMoveRequestEntity copyRq = new NodeCopyMoveRequestEntity();
        copyRq.setTargetParentId(documentLibraryNodeId);

        NodeResponseEntity copyNode = nodes.copyNode(createdNode.getId(), copyRq);

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/site-2", knownFilesSpecificSite2);

        assertNotNull(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(copyNode.getId())));

        // 3) check generic filing for non-explicitly configured sites
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "genericly-routed-site-1", "Generic Site 1");

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);
        // can use same content as we are looking for difference in paths
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-1",
                knownFilesGenericSite1);

        assertNotNull(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 2) copy to 2nd generic site and verify new, identical content was created
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "genericly-routed-site-2", "Generic Site 2");

        copyRq.setTargetParentId(documentLibraryNodeId);

        copyNode = nodes.copyNode(createdNode.getId(), copyRq);

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-2",
                knownFilesGenericSite2);

        assertNotNull(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(copyNode.getId())));
    }

    @Test
    public void siteRoutingFileStore_copyToSiteWithDifferentStoreWithoutOnCopyMoveHandling() throws Exception
    {
        // need to record pre-existing files to exclude in verification
        final Collection<ContentFile> knownFilesSpecificSites = listFilesInAlfData("siteRoutingFileStore2/sharedExplicitSites");
        final Collection<ContentFile> knownFilesGenericSites = listFilesInAlfData("siteRoutingFileStore2/sharedGenericSites");

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

        ContentFile lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore2/sharedExplicitSites",
                knownFilesSpecificSites);

        assertNotNull(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 2) copy to generic site and verify no new content was created
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "genericly-routed-site-3", "Generic Site 3");

        final NodeCopyMoveRequestEntity copyRq = new NodeCopyMoveRequestEntity();
        copyRq.setTargetParentId(documentLibraryNodeId);

        final NodeResponseEntity copyNode = nodes.copyNode(createdNode.getId(), copyRq);

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore2/sharedGenericSites", knownFilesGenericSites);

        assertNull(lastModifiedFileInContent);
        assertTrue(contentMatches(contentBytes, nodes.getContent(copyNode.getId())));
    }

    @Test
    public void siteRoutingFileStore_copyToSiteWithSameStore() throws Exception
    {
        // need to record pre-existing files to exclude in verification
        final Collection<ContentFile> knownFilesSpecificSite1 = listFilesInAlfData("siteRoutingFileStore1/site-1");
        final Collection<ContentFile> knownFilesGenericSite1 = listFilesInAlfData(
                "siteRoutingFileStore1/.otherSites/genericly-routed-site-1");

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

        ContentFile lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/site-1", knownFilesSpecificSite1);

        assertNotNull(lastModifiedFileInContent);
        knownFilesSpecificSite1.add(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 2) copy to same site and verify no new content file was created
        final NodeCopyMoveRequestEntity copyRq = new NodeCopyMoveRequestEntity();
        copyRq.setTargetParentId(documentLibraryNodeId);
        copyRq.setName("Copy of " + createdNode.getName());

        NodeResponseEntity copyNode = nodes.copyNode(createdNode.getId(), copyRq);

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/site-1", knownFilesSpecificSite1);

        assertNull(lastModifiedFileInContent);
        assertTrue(contentMatches(contentBytes, nodes.getContent(copyNode.getId())));

        // 3) check generic filing for non-explicitly configured sites
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "genericly-routed-site-1", "Generic Site 1");

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);
        // can use same content as we are looking for difference in paths
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-1",
                knownFilesGenericSite1);

        assertNotNull(lastModifiedFileInContent);
        knownFilesGenericSite1.add(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 4) copy to same site and verify no new content file was created
        copyRq.setTargetParentId(documentLibraryNodeId);
        copyRq.setName("Copy of " + createdNode.getName());

        copyNode = nodes.copyNode(createdNode.getId(), copyRq);

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-1",
                knownFilesGenericSite1);

        assertNull(lastModifiedFileInContent);
        assertTrue(contentMatches(contentBytes, nodes.getContent(copyNode.getId())));
    }

    @Test
    public void siteRoutingFileStore_moveToSiteWithDifferentStoreWithOnCopyMoveHandling() throws Exception
    {
        // need to record pre-existing files to exclude in verification
        final Collection<ContentFile> knownFilesSpecificSite1 = listFilesInAlfData("siteRoutingFileStore1/site-1");
        final Collection<ContentFile> knownFilesSpecificSite2 = listFilesInAlfData("siteRoutingFileStore1/site-2");
        final Collection<ContentFile> knownFilesGenericSite1 = listFilesInAlfData(
                "siteRoutingFileStore1/.otherSites/genericly-routed-site-1");
        final Collection<ContentFile> knownFilesGenericSite2 = listFilesInAlfData(
                "siteRoutingFileStore1/.otherSites/genericly-routed-site-2");

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

        ContentFile lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/site-1", knownFilesSpecificSite1);

        assertNotNull(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 2) move to 2nd explicit site and verify new content was created + previous (orphaned) content eager deleted
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "explicitly-routed-site-2", "Explicit Site 2");

        final NodeCopyMoveRequestEntity moveRq = new NodeCopyMoveRequestEntity();
        moveRq.setTargetParentId(documentLibraryNodeId);

        nodes.moveNode(createdNode.getId(), moveRq);

        assertFalse(exists(lastModifiedFileInContent));
        knownFilesSpecificSite1.remove(lastModifiedFileInContent);

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/site-2", knownFilesSpecificSite2);

        assertNotNull(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 3) check generic filing for non-explicitly configured sites
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "genericly-routed-site-1", "Generic Site 1");

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);
        // can use same content as we are looking for difference in paths
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-1",
                knownFilesGenericSite1);

        assertNotNull(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 2) move to 2nd generic site and verify new content was created + previous (orphaned) content eager deleted
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "genericly-routed-site-2", "Generic Site 2");

        moveRq.setTargetParentId(documentLibraryNodeId);

        nodes.moveNode(createdNode.getId(), moveRq);

        assertFalse(exists(lastModifiedFileInContent));
        knownFilesGenericSite1.remove(lastModifiedFileInContent);

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-2",
                knownFilesGenericSite2);

        assertNotNull(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));
    }

    @Test
    public void siteRoutingFileStore_moveToSiteWithDifferentStoreWithoutOnCopyMoveHandling() throws Exception
    {
        // need to record pre-existing files to exclude in verification
        final Collection<ContentFile> knownFilesSpecificSites = listFilesInAlfData("siteRoutingFileStore2/sharedExplicitSites");
        final Collection<ContentFile> knownFilesGenericSites = listFilesInAlfData("siteRoutingFileStore2/sharedGenericSites");

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

        ContentFile lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore2/sharedExplicitSites",
                knownFilesSpecificSites);

        assertNotNull(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 2) move to generic site and verify no new content was created while old still exists
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "genericly-routed-site-3", "Generic Site 3");

        final NodeCopyMoveRequestEntity moveRq = new NodeCopyMoveRequestEntity();
        moveRq.setTargetParentId(documentLibraryNodeId);

        nodes.moveNode(createdNode.getId(), moveRq);

        assertTrue(exists(lastModifiedFileInContent));

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore2/sharedGenericSites", knownFilesGenericSites);

        assertNull(lastModifiedFileInContent);
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));
    }

    @Test
    public void propertyRoutingStore_moveEnabledStore() throws Exception
    {
        // need to record pre-existing files to exclude in verification
        final Collection<ContentFile> knownFilesFallbackStore = listFilesInAlfData("propertySelectorFallbackFileStore");
        final Collection<ContentFile> knownFilesStore1 = listFilesInAlfData("propertySelectorFileStore11");
        final Collection<ContentFile> knownFilesStore2 = listFilesInAlfData("propertySelectorFileStore12");

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

        ContentFile lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFallbackFileStore", knownFilesFallbackStore);
        // not adding to exclusions as we later will move it back, resulting in the same file
        // exclusionsFallbackStore.add(lastModifiedFileInContent);

        assertNotNull(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 2) test move when property value is set
        final CommonNodeEntity<PermissionsInfo> nodeUpdate = new CommonNodeEntity<>();
        nodeUpdate.setProperty("aco6scst:selectorProperty", "store1");
        nodes.updateNode(createdNode.getId(), nodeUpdate);

        assertFalse(exists(lastModifiedFileInContent));
        knownFilesFallbackStore.remove(lastModifiedFileInContent);

        lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFileStore11", knownFilesStore1);

        assertNotNull(lastModifiedFileInContent);
        knownFilesStore1.add(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 3) test move to fallback when unmapped property value is set
        nodeUpdate.setProperty("aco6scst:selectorProperty", "nonExistingStore");
        nodes.updateNode(createdNode.getId(), nodeUpdate);

        assertFalse(exists(lastModifiedFileInContent));
        knownFilesStore1.remove(lastModifiedFileInContent);

        lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFallbackFileStore", knownFilesFallbackStore);

        assertNotNull(lastModifiedFileInContent);
        knownFilesFallbackStore.add(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 4) test move/copy to store via copy + setting of selector property
        final NodeCopyMoveRequestEntity copyRq = new NodeCopyMoveRequestEntity();
        copyRq.setName(UUID.randomUUID().toString() + ".txt");
        copyRq.setTargetParentId(documentLibraryNodeId);

        final NodeResponseEntity copiedNode = nodes.copyNode(createdNode.getId(), copyRq);

        nodeUpdate.setProperty("aco6scst:selectorProperty", "store2");
        nodes.updateNode(copiedNode.getId(), nodeUpdate);

        assertTrue(exists(lastModifiedFileInContent));

        lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFileStore12", knownFilesStore2);

        assertNotNull(lastModifiedFileInContent);
        knownFilesStore2.add(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 5) test update of copy and deletion of copied content
        contentBytes = LoremIpsum.getInstance().getParagraphs(1, 10).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(copiedNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        assertFalse(exists(lastModifiedFileInContent));
        knownFilesStore2.remove(lastModifiedFileInContent);

        lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFileStore12", knownFilesStore2);

        assertNotNull(lastModifiedFileInContent);
        knownFilesStore2.add(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(copiedNode.getId())));

        // 6) test property to prevent move
        nodeUpdate.setProperty("aco6scst:moveStoreOnSelectorChange", "false");
        nodeUpdate.setProperty("aco6scst:selectorProperty", "store1");
        nodes.updateNode(copiedNode.getId(), nodeUpdate);

        assertTrue(exists(lastModifiedFileInContent));

        lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFileStore11", knownFilesStore1);

        assertNull(lastModifiedFileInContent);
        assertTrue(contentMatches(contentBytes, nodes.getContent(copiedNode.getId())));
    }

    @Test
    public void propertyRoutingStore_moveDisabledStore() throws Exception
    {
        // need to record pre-existing files to exclude in verification
        final Collection<ContentFile> knownFilesStore21 = listFilesInAlfData("propertySelectorFileStore21");
        final Collection<ContentFile> knownFilesStore22 = listFilesInAlfData("propertySelectorFileStore22");

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

        ContentFile lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFileStore22", knownFilesStore22);

        assertNotNull(lastModifiedFileInContent);
        knownFilesStore22.add(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 2) test lack of move when property value is set
        final CommonNodeEntity<PermissionsInfo> nodeUpdate = new CommonNodeEntity<>();
        nodeUpdate.setProperty("aco6scst:selectorProperty", "store1");
        nodes.updateNode(createdNode.getId(), nodeUpdate);

        assertTrue(exists(lastModifiedFileInContent));

        final ContentFile lastModifiedFileInStore22 = lastModifiedFileInContent;
        lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFileStore21", knownFilesStore21);

        assertNull(lastModifiedFileInContent);

        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 3) test routing to specific store when content is updated
        contentBytes = LoremIpsum.getInstance().getParagraphs(1, 10).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        assertFalse(exists(lastModifiedFileInStore22));
        knownFilesStore22.remove(lastModifiedFileInStore22);

        lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFileStore21", knownFilesStore21);

        assertNotNull(lastModifiedFileInContent);
        knownFilesStore21.add(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 4) test routing to specific store when property already set during creation
        createRequest.setName(UUID.randomUUID().toString() + ".txt");
        createRequest.setProperty("aco6scst:selectorProperty", "store2");
        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);

        contentBytes = LoremIpsum.getInstance().getParagraphs(1, 10).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFileStore22", knownFilesStore22);

        assertNotNull(lastModifiedFileInContent);
        knownFilesStore22.add(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));
    }

    @Test
    public void typeRoutingStore_moveEnabledStore() throws Exception
    {
        // need to record pre-existing files to exclude in verification
        final Collection<ContentFile> knownFilesFallbackStore = listFilesInAlfData("typeRoutingFallbackFileStore1");
        final Collection<ContentFile> knownFilesStore1 = listFilesInAlfData("typeRoutingFileStore1");

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

        ContentFile lastModifiedFileInContent = findLastModifiedFileInAlfData("typeRoutingFallbackFileStore1", knownFilesFallbackStore);

        assertNotNull(lastModifiedFileInContent);
        knownFilesFallbackStore.add(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 2) test move when type is changed (sub-type of mapped type)
        CommonNodeEntity<PermissionsInfo> nodeUpdate = new CommonNodeEntity<>();
        nodeUpdate.setNodeType("aco6scst:invoiceDocument");
        nodes.updateNode(createdNode.getId(), nodeUpdate);

        assertFalse(exists(lastModifiedFileInContent));
        knownFilesFallbackStore.remove(lastModifiedFileInContent);

        lastModifiedFileInContent = findLastModifiedFileInAlfData("typeRoutingFileStore1", knownFilesStore1);

        assertNotNull(lastModifiedFileInContent);
        knownFilesStore1.add(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 3) test direct filing with mapped type
        createRequest = new NodeCreationRequestEntity();
        createRequest.setName(UUID.randomUUID().toString() + ".txt");
        createRequest.setNodeType("aco6scst:archiveDocument");

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);

        contentBytes = LoremIpsum.getInstance().getParagraphs(1, 10).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("typeRoutingFileStore1", knownFilesStore1);

        assertNotNull(lastModifiedFileInContent);
        knownFilesStore1.add(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 4) test property to prevent move
        createRequest = new NodeCreationRequestEntity();
        createRequest.setName(UUID.randomUUID().toString() + ".txt");
        createRequest.setNodeType("cm:content");

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);

        contentBytes = LoremIpsum.getInstance().getParagraphs(1, 10).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("typeRoutingFallbackFileStore1", knownFilesFallbackStore);

        assertNotNull(lastModifiedFileInContent);
        knownFilesFallbackStore.add(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        nodeUpdate = new CommonNodeEntity<>();
        nodeUpdate.setProperty("aco6scst:moveStoreOnSelectorChange", "false");
        nodes.updateNode(createdNode.getId(), nodeUpdate);

        nodeUpdate = new CommonNodeEntity<>();
        nodeUpdate.setNodeType("aco6scst:invoiceDocument");
        nodes.updateNode(createdNode.getId(), nodeUpdate);

        assertTrue(exists(lastModifiedFileInContent));

        lastModifiedFileInContent = findLastModifiedFileInAlfData("typeRoutingFileStore1", knownFilesStore1);

        assertNull(lastModifiedFileInContent);
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));
    }

    @Test
    public void typeRoutingStore_moveDisabledStore() throws Exception
    {
        // need to record pre-existing files to exclude in verification
        final Collection<ContentFile> knownFilesFallbackStore = listFilesInAlfData("typeRoutingFallbackFileStore2");
        final Collection<ContentFile> knownFilesStore2 = listFilesInAlfData("typeRoutingFileStore2");

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

        ContentFile lastModifiedFileInContent = findLastModifiedFileInAlfData("typeRoutingFallbackFileStore2", knownFilesFallbackStore);

        assertNotNull(lastModifiedFileInContent);
        knownFilesFallbackStore.add(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 2) test non-move when type is changed
        final CommonNodeEntity<PermissionsInfo> nodeUpdate = new CommonNodeEntity<>();
        nodeUpdate.setNodeType("aco6scst:archiveDocument");
        nodes.updateNode(createdNode.getId(), nodeUpdate);

        assertTrue(exists(lastModifiedFileInContent));

        lastModifiedFileInContent = findLastModifiedFileInAlfData("typeRoutingFileStore2", knownFilesStore2);

        assertNull(lastModifiedFileInContent);
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // 3) test direct filing with sub-type of mapped type
        createRequest = new NodeCreationRequestEntity();
        createRequest.setName(UUID.randomUUID().toString() + ".txt");
        createRequest.setNodeType("aco6scst:invoiceDocument");

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);

        contentBytes = LoremIpsum.getInstance().getParagraphs(1, 10).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("typeRoutingFileStore2", knownFilesFallbackStore);

        assertNotNull(lastModifiedFileInContent);
        knownFilesStore2.add(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));
    }

    @Test
    public void siteRoutingStore_copyMoveBetweenSitesWithSharedFileStore() throws Exception
    {
        final Collection<ContentFile> knownFilesFallbackStore = listFilesInAlfData("propertySelectorFallbackFileStore");

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

        ContentFile lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFallbackFileStore", knownFilesFallbackStore);

        assertNotNull(lastModifiedFileInContent);
        knownFilesFallbackStore.add(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // test move across sites with same fallback store (selector property is not set)
        final NodeCopyMoveRequestEntity moveCopyRq = new NodeCopyMoveRequestEntity();
        moveCopyRq.setTargetParentId(documentLibrary2NodeId);

        nodes.moveNode(createdNode.getId(), moveCopyRq);

        assertTrue(exists(lastModifiedFileInContent));

        lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFallbackFileStore", knownFilesFallbackStore);

        assertNull(lastModifiedFileInContent);
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // create second test content
        createRequest.setName(UUID.randomUUID().toString() + ".txt");
        createdNode = nodes.createNode(documentLibrary1NodeId, createRequest);

        contentBytes = LoremIpsum.getInstance().getParagraphs(1, 10).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFallbackFileStore", knownFilesFallbackStore);

        assertNotNull(lastModifiedFileInContent);
        knownFilesFallbackStore.add(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // test copy across sites with same fallback store (selector property is not set)
        final NodeResponseEntity copiedNode = nodes.copyNode(createdNode.getId(), moveCopyRq);

        assertTrue(exists(lastModifiedFileInContent));

        final ContentFile sharedFileInContent = lastModifiedFileInContent;
        lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFallbackFileStore", knownFilesFallbackStore);

        assertNull(lastModifiedFileInContent);
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));
        assertTrue(contentMatches(contentBytes, nodes.getContent(copiedNode.getId())));

        // test that shared content is not deleted if either of the copies is deleted
        nodes.deleteNode(createdNode.getId());

        assertTrue(exists(sharedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(copiedNode.getId())));
    }

    @Test
    public void siteRoutingStore_copyMoveBetweenSitesWithDistinctFileStore() throws Exception
    {
        final Collection<ContentFile> knownFilesStore11 = listFilesInAlfData("propertySelectorFileStore11");
        final Collection<ContentFile> knownFilesStore21 = listFilesInAlfData("propertySelectorFileStore21");

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

        ContentFile lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFileStore11", knownFilesStore11);

        assertNotNull(lastModifiedFileInContent);
        knownFilesStore11.add(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // test move across sites with different store (selector property is set)
        final NodeCopyMoveRequestEntity moveCopyRq = new NodeCopyMoveRequestEntity();
        moveCopyRq.setTargetParentId(documentLibrary2NodeId);

        nodes.moveNode(createdNode.getId(), moveCopyRq);

        assertFalse(exists(lastModifiedFileInContent));
        knownFilesStore11.remove(lastModifiedFileInContent);

        lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFileStore21", knownFilesStore21);

        assertNotNull(lastModifiedFileInContent);
        knownFilesStore21.add(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // create second test content
        createRequest.setName(UUID.randomUUID().toString());
        createdNode = nodes.createNode(documentLibrary1NodeId, createRequest);

        contentBytes = LoremIpsum.getInstance().getParagraphs(1, 10).getBytes(StandardCharsets.UTF_8);
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFileStore11", knownFilesStore11);

        assertNotNull(lastModifiedFileInContent);
        knownFilesStore11.add(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(createdNode.getId())));

        // test copy across sites with different store (selector property is set)
        final NodeResponseEntity copiedNode = nodes.copyNode(createdNode.getId(), moveCopyRq);

        assertTrue(exists(lastModifiedFileInContent));
        final ContentFile lastModifiedFileInContentStore11 = lastModifiedFileInContent;

        lastModifiedFileInContent = findLastModifiedFileInAlfData("propertySelectorFileStore21", knownFilesStore21);

        assertNotNull(lastModifiedFileInContent);
        knownFilesStore21.add(lastModifiedFileInContent);
        assertEquals(contentBytes.length, lastModifiedFileInContent.getSizeInContainer());
        assertTrue(contentMatches(contentBytes, lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(copiedNode.getId())));

        // test that specific content is deleted if either of the copies is deleted
        nodes.deleteNode(createdNode.getId(), true);

        assertFalse(exists(lastModifiedFileInContentStore11));
        assertTrue(exists(lastModifiedFileInContent));
        assertTrue(contentMatches(contentBytes, nodes.getContent(copiedNode.getId())));
    }
}
