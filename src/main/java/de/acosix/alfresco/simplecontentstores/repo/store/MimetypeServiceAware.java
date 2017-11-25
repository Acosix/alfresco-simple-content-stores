/*
 * Copyright 2017 Acosix GmbH
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
package de.acosix.alfresco.simplecontentstores.repo.store;

import org.alfresco.repo.content.AbstractContentWriter;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.MimetypeService;

/**
 * This is a placeholder / backport of the interface introduced in Alfresco 5.0 in order to support any implementation of
 * {@link ContentWriter} to be able to have a reference to the {@link MimetypeService} set without requiring sub-classing of
 * {@link AbstractContentWriter}.
 *
 * @author Axel Faust
 */
public interface MimetypeServiceAware
{

    void setMimetypeService(MimetypeService mimetypeService);
}
