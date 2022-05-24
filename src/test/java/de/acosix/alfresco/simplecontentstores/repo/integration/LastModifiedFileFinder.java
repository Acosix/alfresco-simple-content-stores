/*
 * Copyright 2017 - 2022 Acosix GmbH
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
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.HashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Axel Faust
 */
public class LastModifiedFileFinder implements FileVisitor<Path>
{

    private static final Logger LOGGER = LoggerFactory.getLogger(LastModifiedFileFinder.class);

    private final Collection<Path> exclusions;

    private long lastModifiedStamp = -1;

    private Path lastModifiedFile;

    public LastModifiedFileFinder(final Collection<Path> exclusions)
    {
        this.exclusions = new HashSet<>(exclusions);
    }

    /**
     * @return the lastModifiedFile
     */
    public Path getLastModifiedFile()
    {
        return this.lastModifiedFile;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException
    {
        return FileVisitResult.CONTINUE;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException
    {
        // since some file systems may have shoddy "lastModifiedTime" resolutions (*cough* Windows NTFS *cough*) we support an exclusion
        // list to avoid files we already found in previous runs
        // also: depending on API used, content-less nodes may still create 0-byte content files, which we don't care about
        if (!this.exclusions.contains(file) && Files.size(file) > 0)
        {
            final long lastModifiedTime = Files.getLastModifiedTime(file).toMillis();
            if (this.lastModifiedStamp == -1 || lastModifiedTime > this.lastModifiedStamp)
            {
                this.lastModifiedFile = file;
                this.lastModifiedStamp = lastModifiedTime;
            }
        }

        return FileVisitResult.CONTINUE;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public FileVisitResult visitFileFailed(final Path file, final IOException exc) throws IOException
    {
        LOGGER.warn("Failed to visit {}", file, exc);
        return FileVisitResult.CONTINUE;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException
    {
        return FileVisitResult.CONTINUE;
    }

}
