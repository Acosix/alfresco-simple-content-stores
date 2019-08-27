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
package de.acosix.alfresco.simplecontentstores.repo;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;

import org.alfresco.util.GUID;

/**
 * @author Axel Faust
 */
public class TestUtilities
{

    /**
     * Interface of to support test utilities similar to {@code java.util.Function}
     *
     * @author Axel Faust
     *
     * @param <P>
     *            the type of the parameter to this visitor
     * @param <R>
     *            the type of the result of this visitor
     */
    public static interface AggregatingVisitor<P, R>
    {

        /**
         * Visits a specific element
         *
         * @param param
         *            the parameter to visit
         */
        void visit(P param);

        /**
         * Returns the aggregate of this visitor
         *
         * @return the aggregate result of the visitor over all visisted elements
         */
        R getAggregate();
    }

    /**
     * Interface of {@code java.util.Consumer} backported to allow more similar unit tests between the branches of this module.
     *
     * @author Axel Faust
     *
     * @param <P>
     *            the type of the parameter to this function
     */
    public static interface Consumer<P>
    {

        /**
         * Applies this function on the specified parameter
         *
         * @param param
         *            the parameter to the function
         * @return the result of the function
         */
        void accept(P param);
    }

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
            walk(folder.toPath(), new Consumer<Path>()
            {

                /**
                 *
                 * {@inheritDoc}
                 */
                @Override
                public void accept(final Path path)
                {
                    try
                    {
                        Files.delete(path);
                    }
                    catch (final IOException ignore)
                    {
                        // ignore
                    }
                }
            });
        }
        catch (final IOException ignore)
        {
            // ignore
        }
    }

    public static void walk(final Path path, final Consumer<Path> consumer, final FileVisitOption... options) throws IOException
    {
        // use walkFileTree in the same depth-first manner as 1.8+ Files.list()
        Files.walkFileTree(path, options.length == 0 ? Collections.<FileVisitOption> emptySet() : EnumSet.copyOf(Arrays.asList(options)),
                Integer.MAX_VALUE, new SimpleFileVisitor<Path>()
                {

                    /**
                     *
                     * {@inheritDoc}
                     */
                    @Override
                    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
                    {
                        consumer.accept(file);
                        return FileVisitResult.CONTINUE;
                    }

                    /**
                     *
                     * {@inheritDoc}
                     */
                    @Override
                    public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException
                    {
                        if (exc != null)
                        {
                            throw exc;
                        }
                        consumer.accept(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
    }

    public static <R> R walk(final Path path, final AggregatingVisitor<Path, R> aggregator, final FileVisitOption... options)
            throws IOException
    {
        // use walkFileTree in the same depth-first manner as 1.8+ Files.list()
        Files.walkFileTree(path, options.length == 0 ? Collections.<FileVisitOption> emptySet() : EnumSet.copyOf(Arrays.asList(options)),
                Integer.MAX_VALUE, new SimpleFileVisitor<Path>()
                {

                    /**
                     *
                     * {@inheritDoc}
                     */
                    @Override
                    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
                    {
                        aggregator.visit(file);
                        return FileVisitResult.CONTINUE;
                    }

                    /**
                     *
                     * {@inheritDoc}
                     */
                    @Override
                    public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException
                    {
                        if (exc != null)
                        {
                            throw exc;
                        }
                        aggregator.visit(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
        return aggregator.getAggregate();
    }
}
