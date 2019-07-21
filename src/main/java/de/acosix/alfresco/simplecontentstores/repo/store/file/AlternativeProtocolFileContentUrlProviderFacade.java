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
package de.acosix.alfresco.simplecontentstores.repo.store.file;

import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.content.filestore.FileContentUrlProvider;
import org.alfresco.util.ParameterCheck;

/**
 * This facade allows a {@link FileContentStore} to wrap a provided {@link FileContentUrlProvider} and update emitted URLs with the proper
 * store protocol (as configured on the store itself) in order to re-use an existing URL generation strategy while still maintaining control
 * over the protocol for the sake of differentiation among multiple stores.
 *
 * @author Axel Faust
 */
public class AlternativeProtocolFileContentUrlProviderFacade implements FileContentUrlProvider
{

    protected final FileContentUrlProvider delegate;

    protected final String desiredStoreProtocol;

    public AlternativeProtocolFileContentUrlProviderFacade(final FileContentUrlProvider delegate, final String desiredStoreProtocol)
    {
        ParameterCheck.mandatory("delegate", delegate);
        ParameterCheck.mandatoryString("desiredStoreProtocol", desiredStoreProtocol);

        this.delegate = delegate;
        this.desiredStoreProtocol = desiredStoreProtocol;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String createNewFileStoreUrl()
    {
        String fileStoreUrl = this.delegate.createNewFileStoreUrl();

        final int delimiterStart = fileStoreUrl.indexOf(ContentStore.PROTOCOL_DELIMITER);
        fileStoreUrl = this.desiredStoreProtocol + fileStoreUrl.substring(delimiterStart, fileStoreUrl.length());

        return fileStoreUrl;
    }

}
