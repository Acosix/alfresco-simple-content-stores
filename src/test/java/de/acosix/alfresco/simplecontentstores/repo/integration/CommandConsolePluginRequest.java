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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * This DTO class describes the (extremely simple) request payload for command console plugin requests.
 *
 * @author Axel Faust
 */
public class CommandConsolePluginRequest
{

    private List<String> arguments = Collections.emptyList();

    public static CommandConsolePluginRequest from(final String... args)
    {
        final CommandConsolePluginRequest request = new CommandConsolePluginRequest();
        request.setArguments(Arrays.asList(args));
        return request;
    }

    /**
     * @return the arguments
     */
    public List<String> getArguments()
    {
        return new ArrayList<>(this.arguments);
    }

    /**
     * @param arguments
     *            the arguments to set
     */
    public void setArguments(final List<String> arguments)
    {
        this.arguments = arguments != null ? new ArrayList<>(arguments) : Collections.emptyList();
    }

}
