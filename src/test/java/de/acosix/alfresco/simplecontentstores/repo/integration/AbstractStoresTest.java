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

import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.alfresco.service.cmr.site.SiteService;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.internal.LocalResteasyProviderFactory;
import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl;
import org.jboss.resteasy.core.providerfactory.ResteasyProviderFactoryImpl;
import org.jboss.resteasy.plugins.providers.RegisterBuiltin;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    protected static class ContentFile
    {

        private final String pathInContainer;

        private final long sizeInContainer;

        private final LocalDateTime modifiedTimeInContainer;

        private ContentFile(final String path, final long size, final LocalDateTime modifiedTime)
        {
            this.pathInContainer = path;
            this.sizeInContainer = size;
            this.modifiedTimeInContainer = modifiedTime;
        }

        /**
         * @return the pathInContainer
         */
        public String getPathInContainer()
        {
            return this.pathInContainer;
        }

        /**
         * @return the sizeInContainer
         */
        public long getSizeInContainer()
        {
            return this.sizeInContainer;
        }

        /**
         * @return the modifiedTimeInContainer
         */
        public LocalDateTime getModifiedTimeInContainer()
        {
            return this.modifiedTimeInContainer;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString()
        {
            final StringBuilder builder = new StringBuilder();
            builder.append("ContentFile [pathInContainer=");
            builder.append(this.pathInContainer);
            builder.append(", sizeInContainer=");
            builder.append(this.sizeInContainer);
            builder.append(", modifiedTimeInContainer=");
            builder.append(this.modifiedTimeInContainer);
            builder.append("]");
            return builder.toString();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode()
        {
            return Objects.hash(this.modifiedTimeInContainer, this.pathInContainer, this.sizeInContainer);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(final Object obj)
        {
            if (this == obj)
            {
                return true;
            }
            if (obj == null)
            {
                return false;
            }
            if (this.getClass() != obj.getClass())
            {
                return false;
            }
            final ContentFile other = (ContentFile) obj;
            return Objects.equals(this.modifiedTimeInContainer, other.modifiedTimeInContainer)
                    && Objects.equals(this.pathInContainer, other.pathInContainer) && this.sizeInContainer == other.sizeInContainer;
        }

    }

    private static final String DOCKER_REPOSITORY_CONTAINER_NAME = "docker-acosix-simple-content-stores-repository-1";

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractStoresTest.class);

    private static final DockerClient DOCKER_CLIENT;

    private static final DateTimeFormatter FIND_PRINTF_TIME_FORMATTER;

    private static final String NO_SUCH_FILE_OR_DIRECTORY = " No such file or directory\n";

    static
    {
        final DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        final DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder().dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig()).maxConnections(10).connectionTimeout(Duration.ofSeconds(5))
                .responseTimeout(Duration.ofSeconds(10)).build();
        DOCKER_CLIENT = DockerClientImpl.getInstance(config, httpClient);

        FIND_PRINTF_TIME_FORMATTER = new DateTimeFormatterBuilder().parseCaseInsensitive().append(DateTimeFormatter.ISO_LOCAL_DATE)
                .appendLiteral('+').appendValue(HOUR_OF_DAY, 2).appendLiteral(':').appendValue(MINUTE_OF_HOUR, 2).optionalStart()
                .appendLiteral(':').appendValue(SECOND_OF_MINUTE, 2).appendFraction(NANO_OF_SECOND, 0, 9, true).appendLiteral('0')
                .toFormatter(Locale.ENGLISH);
    }

    protected static final String baseUrl = "http://localhost:8082/alfresco";

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
     *     the client to use for making the ReST API call
     * @param baseUrl
     *     the base URL of the Alfresco instance
     * @param user
     *     the user for which to obtain the ticket
     * @param password
     *     the password of the user
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
     *     the client to use for making ReST API calls
     * @param baseUrl
     *     the base URL of the Alfresco instance
     * @param api
     *     the API interface to facade
     * @param ticket
     *     the authentication ticket to use for calls to the API
     * @return the Java facade of the API
     */
    protected static <T> T createAPI(final ResteasyClient client, final String baseUrl, final Class<T> api, final String ticket)
    {
        final ResteasyWebTarget targetServer = client.target(UriBuilder.fromPath(baseUrl));

        final String base64Token = Base64.encodeBase64String(ticket.getBytes(StandardCharsets.UTF_8));
        final ClientRequestFilter rqAuthFilter = requestContext -> {
            requestContext.getHeaders().add("Authorization", "Basic " + base64Token);
        };
        targetServer.register(rqAuthFilter);

        return targetServer.proxy(api);
    }

    /**
     * Initialised a simple Java facade for calls to a particular Alfresco Public ReST API interface.
     *
     * @param client
     *     the client to use for making ReST API calls
     * @param baseUrl
     *     the base URL of the Alfresco instance
     * @param api
     *     the API interface to facade
     * @param userName
     *     the userName to use for calls to the API
     * @param password
     *     the password to use for calls to the API
     * @return the Java facade of the API
     */
    protected static <T> T createAPI(final ResteasyClient client, final String baseUrl, final Class<T> api, final String userName,
            final String password)
    {
        final ResteasyWebTarget targetServer = client.target(UriBuilder.fromPath(baseUrl));

        final String base64Token = Base64.encodeBase64String((userName + ":" + password).getBytes(StandardCharsets.UTF_8));
        final ClientRequestFilter rqAuthFilter = requestContext -> {
            requestContext.getHeaders().add("Authorization", "Basic " + base64Token);
        };
        targetServer.register(rqAuthFilter);

        return targetServer.proxy(api);
    }

    /**
     * Retrieves the node ID of the document library of a particular site, creating the site, if it does not exist.
     *
     * @param client
     *     the client to use for making ReST API calls
     * @param baseUrl
     *     the base URL of the Alfresco instance
     * @param ticket
     *     the authentication ticket to use for calls to the ResT APIs
     * @param siteId
     *     the ID of the site to retrieve / create
     * @param siteTitle
     *     the title of the site to use if this operation cannot find an existing site and creates one lazily
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
     *     the relative path within {@code alf_data} to use as the context for the lookup
     * @param knownFiles
     *     the list of paths to exclude from consideration of most recently modified file
     * @return the path of the most recently modified file, according to file system attributes
     * @throws IOException
     *     if an error occurs walking the file tree of the specified path
     */
    protected static ContentFile findLastModifiedFileInAlfData(final String subPath, final Collection<ContentFile> knownFiles)
            throws IOException
    {
        // account for txn writes happening after response is committed
        // Public ReST API is funny like that
        try
        {
            Thread.sleep(250);
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException("Interrupted looking for last modified file", e);
        }

        ContentFile referenceFile = null;
        if (!knownFiles.isEmpty())
        {
            final List<ContentFile> knownFilesSorted = new ArrayList<>(knownFiles);
            Collections.sort(knownFilesSorted, (a, b) -> a.getModifiedTimeInContainer().compareTo(b.getModifiedTimeInContainer()));
            referenceFile = knownFilesSorted.get(knownFilesSorted.size() - 1);
        }

        final String pathPrefix = "/usr/local/tomcat/alf_data";
        final String findBasePath = pathPrefix + '/' + subPath + '/';
        String[] cmd;
        if (referenceFile != null)
        {
            cmd = new String[] { "find", findBasePath, "-type", "f", "-printf", "%T+ %s %p\\n", "-newer",
                    referenceFile.getPathInContainer() };
        }
        else
        {
            cmd = new String[] { "find", findBasePath, "-type", "f", "-printf", "%T+ %s %p\\n" };
        }

        final List<ContentFile> contentFiles = runFindInContainer(cmd);
        // -newer does report files with exact same age - even the reference file
        // so we remove explicitly from result list
        contentFiles.removeAll(knownFiles);

        ContentFile lastModifiedFile = null;
        if (!contentFiles.isEmpty())
        {
            Collections.sort(contentFiles, (a, b) -> a.getModifiedTimeInContainer().compareTo(b.getModifiedTimeInContainer()));
            lastModifiedFile = contentFiles.get(contentFiles.size() - 1);
        }

        return lastModifiedFile;
    }

    /**
     * Lists the files in a particular path of the container-internal {@code alf_data} folder.
     *
     * @param subPath
     *     the relative path within {@code alf_data} to use as the context for the lookup
     * @return the list of files in the specified path
     * @throws IOException
     *     if an error occurs walking the file tree of the specified path
     */
    protected static Collection<ContentFile> listFilesInAlfData(final String subPath)
    {
        final String pathPrefix = "/usr/local/tomcat/alf_data";
        return runFindInContainer("find", pathPrefix + '/' + subPath, "-type", "f", "-printf", "%T+ %s %p\\n");
    }

    protected static boolean exists(final ContentFile file)
    {
        // account for txn deletes happening after response is committed
        // Public ReST API is funny like that
        try
        {
            Thread.sleep(250);
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException("Interrupted waiting for txn deletes", e);
        }
        
        final String output = runInContainer(s -> !s.contains(NO_SUCH_FILE_OR_DIRECTORY), "stat", file.getPathInContainer());
        return !output.startsWith("stat: cannot stat ");
    }

    /**
     * Checks whether the content in a file matches the content as specified by a provided array of bytes.
     *
     * @param contentBytes
     *     the expected content
     * @param file
     *     the file to check
     * @return {@code true} if the file matches the expected content, {@code false} otherwise
     */
    protected static boolean contentMatches(final byte[] contentBytes, final ContentFile file)
    {
        final String output = runInContainer("sha256sum", file.getPathInContainer());

        boolean matches = false;
        if (!output.isEmpty() && output.contains(file.getPathInContainer()) && !output.contains(NO_SUCH_FILE_OR_DIRECTORY))
        {
            final String sha256InContainer = output.substring(0, output.indexOf(' '));
            final String sha256Target = DigestUtils.sha256Hex(contentBytes);
            matches = sha256Target.equalsIgnoreCase(sha256InContainer);
        }

        return matches;
    }

    /**
     * Checks whether the content in two files matches.
     *
     * @param fileA
     *     the first of the two files
     * @param fileB
     *     the second of the two files
     * @return {@code true} if the files match, {@code false} otherwise
     */
    protected static boolean contentMatches(final ContentFile fileA, final ContentFile fileB)
    {
        final String output = runInContainer("diff", "-q", fileA.getPathInContainer(), fileB.getPathInContainer());
        return !output.matches(".+ differ[\\s]*$");
    }

    /**
     * Checks whether the content in a response (e.g. from the {@link NodesV1#getContent(String) getContent ReST API operation}) matches the
     * content as specified by a provided array of bytes.
     *
     * @param contentBytes
     *     the expected content
     * @param response
     *     the response to check
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
     *     the expected content
     * @param is
     *     the input stream for accessing the content to check
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
                if (!matches)
                {
                    LOGGER.debug("contentMatches failed due to difference in length - read {} bytes and expected {}", offset,
                            contentBytes.length);
                }
                break;
            }
            else
            {
                if (bytesRead > (contentBytes.length - offset))
                {
                    matches = false;
                    LOGGER.debug("contentMatches failed due to difference in length - read {} bytes and expected {}", offset + bytesRead,
                            contentBytes.length);
                }

                for (int i = 0; i < bytesRead && matches; i++)
                {
                    matches = buff[i] == contentBytes[offset + i];

                    if (!matches)
                    {
                        LOGGER.debug("contentMatches failed due to difference in content at position {}: expected byte {} and found {}",
                                offset + i, contentBytes[offset + i], buff[i]);
                    }
                }

                offset += bytesRead;
            }
        }
        return matches;
    }

    private static String runInContainer(final String... cmd)
    {
        return runInContainer(s -> true, cmd);
    }

    private static String runInContainer(final Predicate<String> isRealErrorTester, final String... cmd)
    {
        final ExecCreateCmdResponse execResponse = DOCKER_CLIENT.execCreateCmd(DOCKER_REPOSITORY_CONTAINER_NAME)
                .withAttachStdout(Boolean.TRUE).withAttachStderr(Boolean.TRUE).withCmd(cmd).exec();

        final StringBuilder buffer = new StringBuilder();
        try
        {
            DOCKER_CLIENT.execStartCmd(execResponse.getId()).exec(new ResultCallback.Adapter<Frame>()
            {

                @Override
                public void onNext(final Frame frame)
                {
                    final String payload = new String(frame.getPayload(), StandardCharsets.UTF_8);
                    if (frame.getStreamType() == StreamType.STDOUT)
                    {
                        LOGGER.trace("Got {} command output: {}", cmd[0], payload);
                        buffer.append(payload);
                    }
                    else if (isRealErrorTester.test(payload))
                    {
                        LOGGER.error("Error output from {} command: {}", cmd[0], payload);
                    }
                    else
                    {
                        LOGGER.debug("Got {} command output (via stderr): {}", cmd[0], payload);
                        buffer.append(payload);
                    }
                }
            }).awaitCompletion();
        }
        catch (final InterruptedException iex)
        {
            throw new RuntimeException("Interrupted waiting for docker exec to complete", iex);
        }
        return buffer.toString();
    }

    private static List<ContentFile> runFindInContainer(final String... findCmd)
    {
        final ExecCreateCmdResponse execResponse = DOCKER_CLIENT.execCreateCmd(DOCKER_REPOSITORY_CONTAINER_NAME)
                .withAttachStdout(Boolean.TRUE).withAttachStderr(Boolean.TRUE).withCmd(findCmd).exec();

        final List<ContentFile> contentFiles = new ArrayList<>();
        final StringBuilder buffer = new StringBuilder();
        try
        {
            DOCKER_CLIENT.execStartCmd(execResponse.getId()).exec(new ResultCallback.Adapter<Frame>()
            {

                @Override
                public void onNext(final Frame frame)
                {
                    final String payload = new String(frame.getPayload(), StandardCharsets.UTF_8);
                    if (frame.getStreamType() == StreamType.STDOUT)
                    {
                        LOGGER.trace("Got find command output: {}", payload);
                        buffer.append(payload);

                        int newLineIdx = -1;
                        while ((newLineIdx = buffer.indexOf("\n")) != -1)
                        {
                            if (newLineIdx == 0)
                            {
                                buffer.deleteCharAt(newLineIdx);
                            }
                            else
                            {
                                final String line = buffer.substring(0, newLineIdx);
                                buffer.delete(0, newLineIdx + 1);

                                final String[] lineFragments = line.split(" ");
                                if (lineFragments.length == 3)
                                {
                                    final LocalDateTime modified = LocalDateTime.from(FIND_PRINTF_TIME_FORMATTER.parse(lineFragments[0]));
                                    final long size = Long.parseLong(lineFragments[1]);
                                    contentFiles.add(new ContentFile(lineFragments[2], size, modified));
                                }
                            }
                        }
                    }
                    else
                    {
                        if (payload.contains(NO_SUCH_FILE_OR_DIRECTORY))
                        {
                            LOGGER.info("Path {} does not exist", findCmd[1]);
                        }
                        else
                        {
                            LOGGER.error("Error output from find command: {}", payload);
                        }
                    }
                }
            }).awaitCompletion();
        }
        catch (final InterruptedException iex)
        {
            throw new RuntimeException("Interrupted waiting for docker exec to complete", iex);
        }

        if (buffer.length() > 0)
        {
            final String line = buffer.toString().trim();
            final String[] lineFragments = line.split(" ");
            if (lineFragments.length == 3)
            {
                final LocalDateTime modified = LocalDateTime.from(FIND_PRINTF_TIME_FORMATTER.parse(lineFragments[0]));
                final long size = Long.parseLong(lineFragments[1]);
                contentFiles.add(new ContentFile(lineFragments[2], size, modified));
            }
        }
        return contentFiles;
    }
}
