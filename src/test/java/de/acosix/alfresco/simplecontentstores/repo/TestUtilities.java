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
package de.acosix.alfresco.simplecontentstores.repo;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import org.alfresco.util.GUID;

/**
 * @author Axel Faust
 */
public class TestUtilities
{

    private TestUtilities()
    {
        // NO-OP
    }

    public static File createFolder() throws IOException
    {
        // use GUID to avoid accidental reuse of folder from previous test runs
        final File folder = new File(System.getProperty("java.io.tmpdir") + "/" + GUID.generate());
        Files.createDirectories(folder.toPath());
        return folder;
    }

    public static void delete(final File folder)
    {
        try
        {
            walkAndConsume(folder.toPath(), stream -> {
                stream.forEach(path -> {
                    try
                    {
                        Files.delete(path);
                    }
                    catch (final IOException ignore)
                    {
                        // ignore
                    }
                });
            });
        }
        catch (final IOException | UncheckedIOException ignore)
        {
            // ignore
        }
    }

    public static void walkAndConsume(final Path path, final Consumer<Stream<Path>> consumer, final FileVisitOption... options)
            throws IOException
    {
        try (Stream<Path> stream = Files.walk(path, options))
        {
            consumer.accept(stream);
        }
    }

    public static <V> V walkAndProcess(final Path path, final Function<Stream<Path>, V> fn, final FileVisitOption... options)
            throws IOException
    {
        try (Stream<Path> stream = Files.walk(path, options))
        {
            return fn.apply(stream);
        }
    }
}
