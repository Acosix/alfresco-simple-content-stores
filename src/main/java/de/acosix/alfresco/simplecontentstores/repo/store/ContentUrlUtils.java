/*
 * Copyright 2017, 2018 Acosix GmbH
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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.alfresco.repo.content.ContentStore;
import org.alfresco.util.ParameterCheck;

/**
 * This class provides utilities to work with content URLs in order to convey different kinds of information - not just the technical
 * identity data encoded in normal Alfresco content URLs.
 *
 * @author Axel Faust
 */
public final class ContentUrlUtils
{

    private static final String PREFIX_DESCRIPTOR_STATIC_PART = "_scsp_";

    private static final Pattern PREFIX_DESCRIPTOR_PATTERN = Pattern.compile("_scsp_(\\d+)");

    private ContentUrlUtils()
    {
        // NO-OP
    }

    /**
     * Checks a content URL for use of the {@link StoreConstants#WILDCARD_PROTOCOL wildcard protocol} and replaces its occurence with the
     * protocol a store actually handles.
     *
     * @param contentUrl
     *            the content URL to check
     * @param protocol
     *            the protocol to use instead of the wildcard protocol
     * @return the content URL with occurence of wildcard protocol replaced with the parameter protocol
     */
    public static String checkAndReplaceWildcardProtocol(final String contentUrl, final String protocol)
    {
        ParameterCheck.mandatoryString("contentUrl", contentUrl);

        String processedContentUrl = contentUrl;
        if (processedContentUrl.startsWith(StoreConstants.WILDCARD_PROTOCOL)
                && processedContentUrl.substring(StoreConstants.WILDCARD_PROTOCOL.length()).startsWith(ContentStore.PROTOCOL_DELIMITER))
        {
            processedContentUrl = protocol + processedContentUrl.substring(StoreConstants.WILDCARD_PROTOCOL.length());
        }

        return processedContentUrl;
    }

    /**
     * Extracts the base content URL from a content URL that may contain informational path elements ("prefixes") that should not be
     * considered to be uniquely identifying path elements.
     *
     * @param contentUrl
     *            the content URL to process
     * @return the base content URL - may be identical to the input if no informational path elements are present in the input
     */
    public static String getBaseContentUrl(final String contentUrl)
    {
        ParameterCheck.mandatoryString("contentUrl", contentUrl);

        final StringBuilder builder = new StringBuilder(contentUrl);
        final int separatorIdx = builder.indexOf(ContentStore.PROTOCOL_DELIMITER);
        final int endOfFirstPathFragment = builder.indexOf("/", separatorIdx + ContentStore.PROTOCOL_DELIMITER.length());

        String baseContentUrl;
        if (endOfFirstPathFragment == -1)
        {
            baseContentUrl = contentUrl;
        }
        else
        {
            final String firstPathFragment = builder.substring(separatorIdx + ContentStore.PROTOCOL_DELIMITER.length(),
                    endOfFirstPathFragment);
            final Matcher matcher = PREFIX_DESCRIPTOR_PATTERN.matcher(firstPathFragment);

            if (matcher.matches())
            {
                final int prefixCount = Integer.parseInt(matcher.group(1));

                // find end of last prefix fragment
                int endOfPreviousPathFragment = endOfFirstPathFragment;
                for (int i = 0; i < prefixCount; i++)
                {
                    endOfPreviousPathFragment = builder.indexOf("/", endOfPreviousPathFragment + 1);
                    if (endOfPreviousPathFragment == -1)
                    {
                        throw new IllegalArgumentException("contentUrl prefix descriptor contains incorrect count of prefix fragments");
                    }
                }

                // remove every prefix fragment
                builder.delete(separatorIdx + ContentStore.PROTOCOL_DELIMITER.length(), endOfPreviousPathFragment + 1);
                baseContentUrl = builder.toString();
            }
            else
            {
                baseContentUrl = contentUrl;
            }
        }

        return baseContentUrl;
    }

    /**
     * Extracts informational path elements ("prefixes") contained in a content URL.
     *
     * @param contentUrl
     *            the content URL from which to extract prefixes
     * @return the list of extracted prefixes
     */
    public static List<String> extractPrefixes(final String contentUrl)
    {
        ParameterCheck.mandatoryString("contentUrl", contentUrl);

        final StringBuilder builder = new StringBuilder(contentUrl);
        final int separatorIdx = builder.indexOf(ContentStore.PROTOCOL_DELIMITER);
        final int endOfFirstPathFragment = builder.indexOf("/", separatorIdx + ContentStore.PROTOCOL_DELIMITER.length());

        final List<String> prefixes = new ArrayList<>();
        if (endOfFirstPathFragment != -1)
        {
            final String firstPathFragment = builder.substring(separatorIdx + ContentStore.PROTOCOL_DELIMITER.length(),
                    endOfFirstPathFragment);
            final Matcher matcher = PREFIX_DESCRIPTOR_PATTERN.matcher(firstPathFragment);

            if (matcher.matches())
            {
                final int prefixCount = Integer.parseInt(matcher.group(1));

                // find end of last prefix fragment
                int endOfPreviousPathFragment = endOfFirstPathFragment;
                for (int i = 0; i < prefixCount; i++)
                {
                    final int nextSlash = builder.indexOf("/", endOfPreviousPathFragment + 1);
                    final String prefix = builder.substring(endOfPreviousPathFragment + 1, nextSlash);
                    endOfPreviousPathFragment = nextSlash;

                    if (endOfPreviousPathFragment == -1)
                    {
                        throw new IllegalArgumentException("contentUrl prefix descriptor contains incorrect count of prefix fragments");
                    }
                    prefixes.add(prefix);
                }
            }
        }

        return prefixes;
    }

    /**
     * Adds informational path elements ("prefixes") into a content URL. These path elements may contain data used by specific store
     * implementations to determine filing-relevant information but should be ignored by other stores for sake of comparing unique content
     * identifiers.
     *
     * @param contentUrl
     *            the content URL to add prefixes to - if the content URL already contains prefixes, the new prefixes will be added and the
     *            existing prefix descriptor will be updated accordingly
     * @param prefixes
     *            the prefixes to add
     * @return the content URL with prefixes added
     */
    public static String getContentUrlWithPrefixes(final String contentUrl, final String... prefixes)
    {
        ParameterCheck.mandatoryString("contentUrl", contentUrl);
        ParameterCheck.mandatory("prefixes", prefixes);

        String contentUrlWithPrefixes;
        if (prefixes.length == 0)
        {
            contentUrlWithPrefixes = contentUrl;
        }
        else
        {
            final StringBuilder builder = new StringBuilder(contentUrl);
            final int separatorIdx = builder.indexOf(ContentStore.PROTOCOL_DELIMITER);

            int nextStartIdx = separatorIdx + ContentStore.PROTOCOL_DELIMITER.length();
            int prefixCount = prefixes.length;

            final int endOfFirstPathFragment = builder.indexOf("/", nextStartIdx);
            if (endOfFirstPathFragment != -1)
            {
                final String firstPathFragment = builder.substring(separatorIdx + ContentStore.PROTOCOL_DELIMITER.length(),
                        endOfFirstPathFragment);
                final Matcher matcher = PREFIX_DESCRIPTOR_PATTERN.matcher(firstPathFragment);

                if (matcher.matches())
                {
                    final int existingPrefixesCount = Integer.parseInt(matcher.group(1));
                    prefixCount += existingPrefixesCount;

                    // remove the existing descriptor - will be re-added with new value
                    builder.delete(nextStartIdx, endOfFirstPathFragment + 1);
                }
            }

            // insert prefix descriptor
            builder.insert(nextStartIdx, PREFIX_DESCRIPTOR_STATIC_PART);
            nextStartIdx += PREFIX_DESCRIPTOR_STATIC_PART.length();
            final String nr = String.valueOf(prefixCount);
            builder.insert(nextStartIdx, nr);
            nextStartIdx += nr.length();
            builder.insert(nextStartIdx++, "/");

            for (final String prefix : prefixes)
            {
                builder.insert(nextStartIdx, prefix);
                nextStartIdx += prefix.length();
                builder.insert(nextStartIdx++, "/");
            }

            contentUrlWithPrefixes = builder.toString();
        }

        return contentUrlWithPrefixes;
    }
}
