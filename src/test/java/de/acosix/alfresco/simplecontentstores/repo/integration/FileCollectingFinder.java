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

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Axel Faust
 */
public class FileCollectingFinder implements FileVisitor<Path>
{

    private static final Logger LOGGER = LoggerFactory.getLogger(FileCollectingFinder.class);

    private final List<Path> collectedFiles = new ArrayList<>();

    /**
     * @return the collectedFiles
     */
    public List<Path> getCollectedFiles()
    {
        return new ArrayList<>(this.collectedFiles);
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
        // also: depending on API used, content-less nodes may still create 0-byte content files, which we don't care about
        if (Files.size(file) > 0)
        {
            this.collectedFiles.add(file);
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
