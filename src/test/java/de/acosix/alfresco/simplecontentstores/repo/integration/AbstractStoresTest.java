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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.alfresco.service.cmr.site.SiteService;
import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.util.Arrays;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.internal.LocalResteasyProviderFactory;
import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl;
import org.jboss.resteasy.core.providerfactory.ResteasyProviderFactoryImpl;
import org.jboss.resteasy.plugins.providers.RegisterBuiltin;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import de.acosix.alfresco.rest.client.api.AuthenticationV1;
import de.acosix.alfresco.rest.client.api.NodesV1;
import de.acosix.alfresco.rest.client.api.SitesV1;
import de.acosix.alfresco.rest.client.jackson.RestAPIBeanDeserializerModifier;
import de.acosix.alfresco.rest.client.model.authentication.TicketEntity;
import de.acosix.alfresco.rest.client.model.authentication.TicketRequest;
import de.acosix.alfresco.rest.client.model.sites.SiteContainerResponseEntity;
import de.acosix.alfresco.rest.client.model.sites.SiteCreationRequestEntity;
import de.acosix.alfresco.rest.client.model.sites.SiteResponseEntity;
import de.acosix.alfresco.rest.client.model.sites.SiteVisibility;
import de.acosix.alfresco.rest.client.resteasy.MultiValuedParamConverterProvider;

/**
 * Base class for all content store tests making use of the dockerised deployment of Alfresco and accessing that deployment via the Alfresco
 * Public Rest API.
 *
 * @author Axel Faust
 */
public abstract class AbstractStoresTest
{

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractStoresTest.class);

    protected static final String baseUrl = "http://localhost:8082/alfresco";

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    /**
     * Configures and constructs a Resteasy client to use for calling the Alfresco Public ReST API in the dockerised deployment.
     *
     * @return the configured client
     */
    protected static ResteasyClient setupResteasyClient()
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

        final ResteasyClient client = new ResteasyClientBuilderImpl().providerFactory(resteasyProviderFactory).build();
        return client;
    }

    /**
     * Obtains an authentication ticket from an Alfresco system via the Public ReST API.
     *
     * @param client
     *            the client to use for making the ReST API call
     * @param baseUrl
     *            the base URL of the Alfresco instance
     * @param user
     *            the user for which to obtain the ticket
     * @param password
     *            the password of the user
     * @return the issued authentication ticket
     */
    protected static String obtainTicket(final ResteasyClient client, final String baseUrl, final String user, final String password)
    {
        final ResteasyWebTarget targetServer = client.target(UriBuilder.fromPath(baseUrl));
        final AuthenticationV1 authentication = targetServer.proxy(AuthenticationV1.class);

        final TicketRequest rq = new TicketRequest();
        rq.setUserId(user);
        rq.setPassword(password);
        final TicketEntity ticket = authentication.createTicket(rq);
        return ticket.getId();
    }

    /**
     * Initialised a simple Java facade for calls to a particular Alfresco Public ReST API interface.
     *
     * @param client
     *            the client to use for making ReST API calls
     * @param baseUrl
     *            the base URL of the Alfresco instance
     * @param api
     *            the API interface to facade
     * @param ticket
     *            the authentication ticket to use for calls to the API
     * @return the Java facade of the API
     */
    protected static <T> T createAPI(final ResteasyClient client, final String baseUrl, final Class<T> api, final String ticket)
    {
        final ResteasyWebTarget targetServer = client.target(UriBuilder.fromPath(baseUrl));

        final ClientRequestFilter rqAuthFilter = (requestContext) -> {
            final String base64Token = Base64.encodeBase64String(ticket.getBytes(StandardCharsets.UTF_8));
            requestContext.getHeaders().add("Authorization", "Basic " + base64Token);
        };
        targetServer.register(rqAuthFilter);

        return targetServer.proxy(api);
    }

    /**
     * Retrieves the node ID of the document library of a particular site, creating the site, if it does not exist.
     *
     * @param client
     *            the client to use for making ReST API calls
     * @param baseUrl
     *            the base URL of the Alfresco instance
     * @param ticket
     *            the authentication ticket to use for calls to the ResT APIs
     * @param siteId
     *            the ID of the site to retrieve / create
     * @param siteTitle
     *            the title of the site to use if this operation cannot find an existing site and creates one lazily
     * @return the node ID of the document library
     */
    protected static String getOrCreateSiteAndDocumentLibrary(final ResteasyClient client, final String baseUrl, final String ticket,
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

    /**
     * Looks up the most recently modified file in a particular path of the Docker-mounted {@code alf_data} folder.
     *
     * @param subPath
     *            the relative path within {@code alf_data} to use as the context for the lookup
     * @param exclusions
     *            the list of paths to exclude from consideration of most recently modified file
     * @return the path of the most recently modified file, according to file system attributes
     * @throws IOException
     *             if an error occurs walking the file tree of the specified path
     */
    protected static Path findLastModifiedFileInAlfData(final String subPath, final Collection<Path> exclusions) throws IOException
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

    /**
     * Lists the files in a particular path of the Docker-mounted {@code alf_data} folder.
     *
     * @param subPath
     *            the relative path within {@code alf_data} to use as the context for the lookup
     * @return the list of paths for existing files in the specified path
     * @throws IOException
     *             if an error occurs walking the file tree of the specified path
     */
    protected static Collection<Path> listFilesInAlfData(final String subPath) throws IOException
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

    /**
     * Checks whether the content in a file matches the content as specified by a provided array of bytes.
     *
     * @param contentBytes
     *            the expected content
     * @param file
     *            the file to check
     * @return {@code true} if the file matches the expected content, {@code false} otherwise
     */
    protected static boolean contentMatches(final byte[] contentBytes, final Path file) throws IOException
    {
        boolean matches;

        try (InputStream is = Files.newInputStream(file))
        {
            matches = contentMatches(contentBytes, is);
        }
        return matches;
    }

    /**
     * Checks whether the content in two files matches.
     *
     * @param fileA
     *            the first of the two files
     * @param fileB
     *            the second of the two files
     * @return {@code true} if the files match, {@code false} otherwise
     */
    protected static boolean contentMatches(final Path fileA, final Path fileB) throws IOException
    {
        boolean matches;

        try (InputStream isA = Files.newInputStream(fileA))
        {
            try (InputStream isB = Files.newInputStream(fileB))
            {
                final byte[] buffA = new byte[8192];
                final byte[] buffB = new byte[8192];

                matches = true;
                while (matches)
                {
                    final int bytesReadA = isA.read(buffA);
                    final int bytesReadB = isB.read(buffB);

                    if (bytesReadA != bytesReadB)
                    {
                        matches = false;
                    }
                    else if (bytesReadA != -1)
                    {
                        // note: don't have to care about equals check including bytes between bytesRead and length
                        // (any left over bytes from previous read would be identical in both buffers)
                        matches = Arrays.areEqual(buffA, buffB);
                    }
                    else
                    {
                        break;
                    }
                }
            }
        }
        return matches;
    }

    /**
     * Checks whether the content in a response (e.g. from the {@link NodesV1#getContent(String) getContent ReST API operation}) matches the
     * content as specified by a provided array of bytes.
     *
     * @param contentBytes
     *            the expected content
     * @param response
     *            the response to check
     * @return {@code true} if the response matches the expected content, {@code false} otherwise
     */
    protected static boolean contentMatches(final byte[] contentBytes, final Response response) throws IOException
    {
        boolean matches = false;

        final Object entity = response.getEntity();
        if (entity instanceof InputStream)
        {
            try (InputStream is = (InputStream) entity)
            {
                matches = contentMatches(contentBytes, is);
            }
        }
        return matches;
    }

    /**
     * Checks whether the content in a stream matches the content as specified by a provided array of bytes.
     *
     * @param contentBytes
     *            the expected content
     * @param is
     *            the input stream for accessing the content to check
     * @return {@code true} if the stream matches the expected content, {@code false} otherwise
     */
    protected static boolean contentMatches(final byte[] contentBytes, final InputStream is) throws IOException
    {
        boolean matches = true;
        int offset = 0;
        final byte[] buff = new byte[8192];

        while (matches)
        {
            final int bytesRead = is.read(buff);

            if (bytesRead == -1)
            {
                matches = offset == contentBytes.length;
                break;
            }
            else
            {
                if (bytesRead > (contentBytes.length - offset))
                {
                    matches = false;
                }

                for (int i = 0; i < bytesRead && matches; i++)
                {
                    matches = buff[i] == contentBytes[offset + i];
                }

                offset += bytesRead;
            }
        }
        return matches;
    }
}
