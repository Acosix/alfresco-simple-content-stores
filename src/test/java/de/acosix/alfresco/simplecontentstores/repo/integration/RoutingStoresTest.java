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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.UriBuilder;

import org.alfresco.service.cmr.site.SiteService;
import org.apache.commons.codec.binary.Base64;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.internal.LocalResteasyProviderFactory;
import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl;
import org.jboss.resteasy.core.providerfactory.ResteasyProviderFactoryImpl;
import org.jboss.resteasy.plugins.providers.RegisterBuiltin;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.thedeanda.lorem.LoremIpsum;

import de.acosix.alfresco.rest.client.api.AuthenticationV1;
import de.acosix.alfresco.rest.client.api.NodesV1;
import de.acosix.alfresco.rest.client.api.SitesV1;
import de.acosix.alfresco.rest.client.jackson.RestAPIBeanDeserializerModifier;
import de.acosix.alfresco.rest.client.model.authentication.TicketEntity;
import de.acosix.alfresco.rest.client.model.authentication.TicketRequest;
import de.acosix.alfresco.rest.client.model.nodes.NodeCopyMoveRequestEntity;
import de.acosix.alfresco.rest.client.model.nodes.NodeCreationRequestEntity;
import de.acosix.alfresco.rest.client.model.nodes.NodeResponseEntity;
import de.acosix.alfresco.rest.client.model.sites.SiteContainerResponseEntity;
import de.acosix.alfresco.rest.client.model.sites.SiteCreationRequestEntity;
import de.acosix.alfresco.rest.client.model.sites.SiteResponseEntity;
import de.acosix.alfresco.rest.client.model.sites.SiteVisibility;
import de.acosix.alfresco.rest.client.resteasy.MultiValuedParamConverterProvider;

/**
 *
 * @author Axel Faust
 */
public class RoutingStoresTest
{

    private static final Logger LOGGER = LoggerFactory.getLogger(RoutingStoresTest.class);

    private static final String baseUrl = "http://localhost:8082/alfresco";

    private static ResteasyClient client;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @BeforeClass
    public static void setup()
    {
        final SimpleModule module = new SimpleModule();
        module.setDeserializerModifier(new RestAPIBeanDeserializerModifier());

        final ResteasyJackson2Provider resteasyJacksonProvider = new ResteasyJackson2Provider();
        final ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(Include.NON_EMPTY);
        mapper.registerModule(module);
        resteasyJacksonProvider.setMapper(mapper);

        final LocalResteasyProviderFactory resteasyProviderFactory = new LocalResteasyProviderFactory(new ResteasyProviderFactoryImpl());
        resteasyProviderFactory.register(resteasyJacksonProvider);
        // will cause a warning regarding Jackson provider which is already registered
        RegisterBuiltin.register(resteasyProviderFactory);
        resteasyProviderFactory.register(new MultiValuedParamConverterProvider());

        client = new ResteasyClientBuilderImpl().providerFactory(resteasyProviderFactory).build();
    }

    @Test
    public void fallbackRoutedToDefaultFileStore() throws Exception
    {
        // need to record pre-existing files to exclude in verification
        final Collection<Path> exclusions = listFilesInAlfData("contentstore");

        final String ticket = obtainTicket(client, baseUrl, "admin", "admin");
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

        final byte[] fileBytes = Files.readAllBytes(lastModifiedFileInContent);
        Assert.assertTrue(Arrays.equals(contentBytes, fileBytes));
    }

    @Test
    public void siteRoutedToSiteRoutingFileStore_uniqueStores() throws Exception
    {
        // need to record pre-existing files to exclude in verification
        final Collection<Path> exclusionsSpecificSite1 = listFilesInAlfData("siteRoutingFileStore1/site-1");
        final Collection<Path> exclusionsSpecificSite2 = listFilesInAlfData("siteRoutingFileStore1/site-2");
        final Collection<Path> exclusionsGenericSite1 = listFilesInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-1");
        final Collection<Path> exclusionsGenericSite2 = listFilesInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-2");

        final String ticket = obtainTicket(client, baseUrl, "admin", "admin");
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

        byte[] fileBytes = Files.readAllBytes(lastModifiedFileInContent);
        Assert.assertTrue(Arrays.equals(contentBytes, fileBytes));

        // 2) verify routing for 2nd explicit site
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "explicitly-routed-site-2", "Explicit Site 2");

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);
        // can use same content as we are looking for difference in paths
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/site-2", exclusionsSpecificSite2);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));

        fileBytes = Files.readAllBytes(lastModifiedFileInContent);
        Assert.assertTrue(Arrays.equals(contentBytes, fileBytes));

        // 3) check generic filing for non-explicitly configured sites
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "genericly-routed-site-1", "Generic Site 1");

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);
        // can use same content as we are looking for difference in paths
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-1",
                exclusionsGenericSite1);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));

        fileBytes = Files.readAllBytes(lastModifiedFileInContent);
        Assert.assertTrue(Arrays.equals(contentBytes, fileBytes));

        // 4) verify generic filing for 2nd non-explicitly configured sites
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "genericly-routed-site-2", "Generic Site 2");

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);
        // can use same content as we are looking for difference in paths
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-2",
                exclusionsGenericSite2);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));

        fileBytes = Files.readAllBytes(lastModifiedFileInContent);
        Assert.assertTrue(Arrays.equals(contentBytes, fileBytes));
    }

    @Test
    public void siteRoutedToSiteRoutingFileStore_sharedStores() throws Exception
    {
        // need to record pre-existing files to exclude in verification
        final Collection<Path> exclusionsSpecificSites = listFilesInAlfData("siteRoutingFileStore2/sharedExplicitSites");
        final Collection<Path> exclusionsGenericSites = listFilesInAlfData("siteRoutingFileStore2/sharedGenericSites");

        final String ticket = obtainTicket(client, baseUrl, "admin", "admin");
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

        byte[] fileBytes = Files.readAllBytes(lastModifiedFileInContent);
        Assert.assertTrue(Arrays.equals(contentBytes, fileBytes));

        // 2) verify routing for 2nd explicit site
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "explicitly-routed-site-4", "Explicit Site 4");

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);
        // can use same content as we are looking for difference in paths
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        exclusionsSpecificSites.add(lastModifiedFileInContent);
        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore2/sharedExplicitSites", exclusionsSpecificSites);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));

        fileBytes = Files.readAllBytes(lastModifiedFileInContent);
        Assert.assertTrue(Arrays.equals(contentBytes, fileBytes));

        // 3) check generic filing for non-explicitly configured sites
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "genericly-routed-site-3", "Generic Site 3");

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);
        // can use same content as we are looking for difference in paths
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore2/sharedGenericSites", exclusionsGenericSites);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));

        fileBytes = Files.readAllBytes(lastModifiedFileInContent);
        Assert.assertTrue(Arrays.equals(contentBytes, fileBytes));

        // 4) verify generic filing for 2nd non-explicitly configured sites
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "genericly-routed-site-4", "Generic Site 4");

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);
        // can use same content as we are looking for difference in paths
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        exclusionsGenericSites.add(lastModifiedFileInContent);
        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore2/sharedGenericSites", exclusionsGenericSites);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));

        fileBytes = Files.readAllBytes(lastModifiedFileInContent);
        Assert.assertTrue(Arrays.equals(contentBytes, fileBytes));
    }

    @Test
    public void siteRoutedToSiteRoutingFileStore_copyToSiteWithDifferentStoreWithOnCopyMoveHandling() throws Exception
    {
        // need to record pre-existing files to exclude in verification
        final Collection<Path> exclusionsSpecificSite1 = listFilesInAlfData("siteRoutingFileStore1/site-1");
        final Collection<Path> exclusionsSpecificSite2 = listFilesInAlfData("siteRoutingFileStore1/site-2");
        final Collection<Path> exclusionsGenericSite1 = listFilesInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-1");
        final Collection<Path> exclusionsGenericSite2 = listFilesInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-2");

        final String ticket = obtainTicket(client, baseUrl, "admin", "admin");
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

        byte[] fileBytes = Files.readAllBytes(lastModifiedFileInContent);
        Assert.assertTrue(Arrays.equals(contentBytes, fileBytes));

        // 2) copy to 2nd explicit site and verify new, identical content was created
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "explicitly-routed-site-2", "Explicit Site 2");

        final NodeCopyMoveRequestEntity copyRq = new NodeCopyMoveRequestEntity();
        copyRq.setTargetParentId(documentLibraryNodeId);

        nodes.copyNode(createdNode.getId(), copyRq);

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/site-2", exclusionsSpecificSite2);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));

        fileBytes = Files.readAllBytes(lastModifiedFileInContent);
        Assert.assertTrue(Arrays.equals(contentBytes, fileBytes));

        // 3) check generic filing for non-explicitly configured sites
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "genericly-routed-site-1", "Generic Site 1");

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);
        // can use same content as we are looking for difference in paths
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-1",
                exclusionsGenericSite1);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));

        fileBytes = Files.readAllBytes(lastModifiedFileInContent);
        Assert.assertTrue(Arrays.equals(contentBytes, fileBytes));

        // 2) copy to 2nd generic site and verify new, identical content was created
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "genericly-routed-site-2", "Generic Site 2");

        copyRq.setTargetParentId(documentLibraryNodeId);

        nodes.copyNode(createdNode.getId(), copyRq);

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-2",
                exclusionsGenericSite2);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));

        fileBytes = Files.readAllBytes(lastModifiedFileInContent);
        Assert.assertTrue(Arrays.equals(contentBytes, fileBytes));
    }

    @Test
    public void siteRoutedToSiteRoutingFileStore_copyToSiteWithDifferentStoreWithoutOnCopyMoveHandling() throws Exception
    {
        // need to record pre-existing files to exclude in verification
        final Collection<Path> exclusionsSpecificSites = listFilesInAlfData("siteRoutingFileStore2/sharedExplicitSites");
        final Collection<Path> exclusionsGenericSites = listFilesInAlfData("siteRoutingFileStore2/sharedGenericSites");

        final String ticket = obtainTicket(client, baseUrl, "admin", "admin");
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

        final byte[] fileBytes = Files.readAllBytes(lastModifiedFileInContent);
        Assert.assertTrue(Arrays.equals(contentBytes, fileBytes));

        // 2) copy to generic site and verify no new content was created
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "genericly-routed-site-3", "Generic Site 3");

        final NodeCopyMoveRequestEntity copyRq = new NodeCopyMoveRequestEntity();
        copyRq.setTargetParentId(documentLibraryNodeId);

        nodes.copyNode(createdNode.getId(), copyRq);

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore2/sharedGenericSites", exclusionsGenericSites);

        Assert.assertNull(lastModifiedFileInContent);
    }

    @Test
    public void siteRoutedToSiteRoutingFileStore_copyToSiteWithSameStore() throws Exception
    {
        // need to record pre-existing files to exclude in verification
        final Collection<Path> exclusionsSpecificSite1 = listFilesInAlfData("siteRoutingFileStore1/site-1");
        final Collection<Path> exclusionsGenericSite1 = listFilesInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-1");

        final String ticket = obtainTicket(client, baseUrl, "admin", "admin");
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

        byte[] fileBytes = Files.readAllBytes(lastModifiedFileInContent);
        Assert.assertTrue(Arrays.equals(contentBytes, fileBytes));

        // 2) copy to same site and verify no new content file was created
        final NodeCopyMoveRequestEntity copyRq = new NodeCopyMoveRequestEntity();
        copyRq.setTargetParentId(documentLibraryNodeId);
        copyRq.setName("Copy of " + createdNode.getName());

        nodes.copyNode(createdNode.getId(), copyRq);

        exclusionsSpecificSite1.add(lastModifiedFileInContent);
        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/site-1", exclusionsSpecificSite1);

        Assert.assertNull(lastModifiedFileInContent);

        // 3) check generic filing for non-explicitly configured sites
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "genericly-routed-site-1", "Generic Site 1");

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);
        // can use same content as we are looking for difference in paths
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-1",
                exclusionsGenericSite1);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));

        fileBytes = Files.readAllBytes(lastModifiedFileInContent);
        Assert.assertTrue(Arrays.equals(contentBytes, fileBytes));

        // 4) copy to same site and verify no new content file was created
        copyRq.setTargetParentId(documentLibraryNodeId);
        copyRq.setName("Copy of " + createdNode.getName());

        nodes.copyNode(createdNode.getId(), copyRq);

        exclusionsGenericSite1.add(lastModifiedFileInContent);
        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-1",
                exclusionsGenericSite1);

        Assert.assertNull(lastModifiedFileInContent);
    }

    @Test
    public void siteRoutedToSiteRoutingFileStore_moveToSiteWithDifferentStoreWithOnCopyMoveHandling() throws Exception
    {
        // need to record pre-existing files to exclude in verification
        final Collection<Path> exclusionsSpecificSite1 = listFilesInAlfData("siteRoutingFileStore1/site-1");
        final Collection<Path> exclusionsSpecificSite2 = listFilesInAlfData("siteRoutingFileStore1/site-2");
        final Collection<Path> exclusionsGenericSite1 = listFilesInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-1");
        final Collection<Path> exclusionsGenericSite2 = listFilesInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-2");

        final String ticket = obtainTicket(client, baseUrl, "admin", "admin");
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

        byte[] fileBytes = Files.readAllBytes(lastModifiedFileInContent);
        Assert.assertTrue(Arrays.equals(contentBytes, fileBytes));

        // 2) move to 2nd explicit site and verify new content was created + previous (orphaned) content eager deleted
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "explicitly-routed-site-2", "Explicit Site 2");

        final NodeCopyMoveRequestEntity moveRq = new NodeCopyMoveRequestEntity();
        moveRq.setTargetParentId(documentLibraryNodeId);

        nodes.moveNode(createdNode.getId(), moveRq);

        Assert.assertFalse(Files.exists(lastModifiedFileInContent));

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/site-2", exclusionsSpecificSite2);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));

        fileBytes = Files.readAllBytes(lastModifiedFileInContent);
        Assert.assertTrue(Arrays.equals(contentBytes, fileBytes));

        // 3) check generic filing for non-explicitly configured sites
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "genericly-routed-site-1", "Generic Site 1");

        createdNode = nodes.createNode(documentLibraryNodeId, createRequest);
        // can use same content as we are looking for difference in paths
        nodes.setContent(createdNode.getId(), new ByteArrayInputStream(contentBytes), "text/plain");

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-1",
                exclusionsGenericSite1);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));

        fileBytes = Files.readAllBytes(lastModifiedFileInContent);
        Assert.assertTrue(Arrays.equals(contentBytes, fileBytes));

        // 2) move to 2nd generic site and verify new content was created + previous (orphaned) content eager deleted
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "genericly-routed-site-2", "Generic Site 2");

        moveRq.setTargetParentId(documentLibraryNodeId);

        nodes.moveNode(createdNode.getId(), moveRq);

        Assert.assertFalse(Files.exists(lastModifiedFileInContent));

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore1/.otherSites/genericly-routed-site-2",
                exclusionsGenericSite2);

        Assert.assertNotNull(lastModifiedFileInContent);
        Assert.assertEquals(contentBytes.length, Files.size(lastModifiedFileInContent));

        fileBytes = Files.readAllBytes(lastModifiedFileInContent);
        Assert.assertTrue(Arrays.equals(contentBytes, fileBytes));
    }

    @Test
    public void siteRoutedToSiteRoutingFileStore_moveToSiteWithDifferentStoreWithoutOnCopyMoveHandling() throws Exception
    {
        // need to record pre-existing files to exclude in verification
        final Collection<Path> exclusionsSpecificSites = listFilesInAlfData("siteRoutingFileStore2/sharedExplicitSites");
        final Collection<Path> exclusionsGenericSites = listFilesInAlfData("siteRoutingFileStore2/sharedGenericSites");

        final String ticket = obtainTicket(client, baseUrl, "admin", "admin");
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

        final byte[] fileBytes = Files.readAllBytes(lastModifiedFileInContent);
        Assert.assertTrue(Arrays.equals(contentBytes, fileBytes));

        // 2) move to generic site and verify no new content was created while old still exists
        documentLibraryNodeId = getOrCreateSiteAndDocumentLibrary(client, baseUrl, ticket, "genericly-routed-site-3", "Generic Site 3");

        final NodeCopyMoveRequestEntity moveRq = new NodeCopyMoveRequestEntity();
        moveRq.setTargetParentId(documentLibraryNodeId);

        nodes.moveNode(createdNode.getId(), moveRq);

        Assert.assertTrue(Files.exists(lastModifiedFileInContent));

        lastModifiedFileInContent = findLastModifiedFileInAlfData("siteRoutingFileStore2/sharedGenericSites", exclusionsGenericSites);

        Assert.assertNull(lastModifiedFileInContent);
    }

    // TODO Tests for other routing store configurations

    private static String obtainTicket(final ResteasyClient client, final String baseUrl, final String user, final String password)
    {
        final ResteasyWebTarget targetServer = client.target(UriBuilder.fromPath(baseUrl));
        final AuthenticationV1 authentication = targetServer.proxy(AuthenticationV1.class);

        final TicketRequest rq = new TicketRequest();
        rq.setUserId(user);
        rq.setPassword(password);
        final TicketEntity ticket = authentication.createTicket(rq);
        return ticket.getId();
    }

    private static <T> T createAPI(final ResteasyClient client, final String baseUrl, final Class<T> api, final String ticket)
    {
        final ResteasyWebTarget targetServer = client.target(UriBuilder.fromPath(baseUrl));

        final ClientRequestFilter rqAuthFilter = (requestContext) -> {
            final String base64Token = Base64.encodeBase64String(ticket.getBytes(StandardCharsets.UTF_8));
            requestContext.getHeaders().add("Authorization", "Basic " + base64Token);
        };
        targetServer.register(rqAuthFilter);

        return targetServer.proxy(api);
    }

    private static String getOrCreateSiteAndDocumentLibrary(final ResteasyClient client, final String baseUrl, final String ticket,
            final String siteId, final String siteTitle)
    {
        final SitesV1 sites = createAPI(client, baseUrl, SitesV1.class, ticket);

        SiteResponseEntity site = null;

        try
        {
            site = sites.getSite(siteId);
        }
        catch (final NotFoundException ignore)
        {
            // getOrCreate implies that site might not exist (yet)
        }

        if (site == null)
        {
            final SiteCreationRequestEntity siteToCreate = new SiteCreationRequestEntity();
            siteToCreate.setId(siteId);
            siteToCreate.setTitle(siteTitle);
            siteToCreate.setVisibility(SiteVisibility.PUBLIC);

            site = sites.createSite(siteToCreate, true, true, null);
        }

        final SiteContainerResponseEntity documentLibrary = sites.getSiteContainer(site.getId(), SiteService.DOCUMENT_LIBRARY);
        return documentLibrary.getId();
    }

    private static Path findLastModifiedFileInAlfData(final String subPath, final Collection<Path> exclusions) throws Exception
    {
        final LastModifiedFileFinder lastModifiedFileFinder = new LastModifiedFileFinder(exclusions);

        final Path alfData = Paths.get("target", "docker", "alf_data");

        final Path startingPoint = subPath != null && !subPath.isEmpty() ? alfData.resolve(subPath) : alfData;
        final Path lastModifiedFile;
        if (Files.exists(startingPoint))
        {
            Files.walkFileTree(startingPoint, lastModifiedFileFinder);

            lastModifiedFile = lastModifiedFileFinder.getLastModifiedFile();

            LOGGER.debug("Last modified file in alf_data/{} is {}", subPath, lastModifiedFile);
        }
        else
        {
            lastModifiedFile = null;
        }

        return lastModifiedFile;
    }

    private static Collection<Path> listFilesInAlfData(final String subPath) throws Exception
    {
        final FileCollectingFinder collectingFinder = new FileCollectingFinder();

        final Path alfData = Paths.get("target", "docker", "alf_data");

        final Path startingPoint = subPath != null && !subPath.isEmpty() ? alfData.resolve(subPath) : alfData;
        final List<Path> files;
        if (Files.exists(startingPoint))
        {
            Files.walkFileTree(startingPoint, collectingFinder);

            files = collectingFinder.getCollectedFiles();
        }
        else
        {
            files = new ArrayList<>();
        }

        return files;
    }
}
