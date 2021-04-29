/*
 * Copyright 2017 - 2021 Acosix GmbH
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

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

/**
 * This interface provides an abstraction over the command console plugin provided for this module so it can be easily consumed in
 * integration tests just like ReST v1 API endpoints.
 *
 * @author Axel Faust
 */
@Path("/s/ootbee/admin/command-console/simple-content-stores")
public interface SimpleContentStoresCommandPlugin
{

    @POST
    @Path("/listEncryptionKeys")
    @Consumes("application/json")
    @Produces("application/json")
    CommandConsolePluginResponse listEncryptionKeys(CommandConsolePluginRequest request);

    @POST
    @Path("/enableEncryptionKey")
    @Consumes("application/json")
    @Produces("application/json")
    CommandConsolePluginResponse enableEncryptionKey(CommandConsolePluginRequest request);

    @POST
    @Path("/disableEncryptionKey")
    @Consumes("application/json")
    @Produces("application/json")
    CommandConsolePluginResponse disableEncryptionKey(CommandConsolePluginRequest request);

    @POST
    @Path("/countEncryptedSymmetricKeys")
    @Consumes("application/json")
    @Produces("application/json")
    CommandConsolePluginResponse countEncryptedSymmetricKeys(CommandConsolePluginRequest request);

    @POST
    @Path("/listEncryptionKeysEligibleForReEncryption")
    @Consumes("application/json")
    @Produces("application/json")
    CommandConsolePluginResponse listEncryptionKeysEligibleForReEncryption(CommandConsolePluginRequest request);

    @POST
    @Path("/reEncryptSymmetricKeys")
    @Consumes("application/json")
    @Produces("application/json")
    CommandConsolePluginResponse reEncryptSymmetricKeys(CommandConsolePluginRequest request);
}
