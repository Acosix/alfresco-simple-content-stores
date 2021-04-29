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
import java.util.List;

/**
 * This DTO class describes the (extremely simple) response payload for command console plugin requests.
 *
 * @author Axel Faust
 */
public class CommandConsolePluginResponse
{

    private List<String> preformattedOutputLines;

    /**
     * @return the preformattedOutputLines
     */
    public List<String> getPreformattedOutputLines()
    {
        return this.preformattedOutputLines != null ? new ArrayList<>(this.preformattedOutputLines) : null;
    }

    /**
     * @param preformattedOutputLines
     *            the preformattedOutputLines to set
     */
    public void setPreformattedOutputLines(final List<String> preformattedOutputLines)
    {
        this.preformattedOutputLines = preformattedOutputLines != null ? new ArrayList<>(preformattedOutputLines) : null;
    }

}
