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
package de.acosix.alfresco.simplecontentstores.repo.dao;

import java.util.Collection;

import org.alfresco.repo.domain.contentdata.ContentUrlEntity;
import org.alfresco.util.Pair;

/**
 * Instances of this interface expose content URL-related functionality to fill gaps in Alfresco Content Services' handling of content URLs,
 * specifically fixing consistency issues around the invalidation of cache entries and cases of un-orphaning.
 *
 * @author Axel Faust
 */
public interface ContentUrlConsistencyDAO
{

    /**
     * Checks whether a specific content URL is currently unreferenced and retrieves its current state.
     *
     * @param contentUrl
     *     the content URL to check
     * @return the content URL entity and the result of the check - {@code if the entity does not exist at all}
     */
    Pair<ContentUrlEntity, Boolean> getContentUrlEntityUnreferenced(String contentUrl);

    /**
     * Removes the orphan time of a content URL
     *
     * @param id
     *     the unique ID of the entity to un-orphan
     * @param oldOrphanTime
     *     the orphan time we expect to update for optimistic locking
     * @return {@code true} if the operation did update a content URL entity, {@code false} otherwise
     */
    boolean unoprhanContentUrl(Long id, Long oldOrphanTime);

    /**
     * Invalidates the cache entry for a content URLs.
     *
     * @param contentUrl
     *     the content URL for which to invalidate the cache entry
     */
    void invalidateCachedContentUrlEntity(String contentUrl);

    /**
     * Invalidates cache entries for a collection of content URLs.
     *
     * @param contentUrls
     *     the content URLs for which to invalidate cache entries
     */
    void invalidateCachedContentUrlEntities(Collection<String> contentUrls);
}
