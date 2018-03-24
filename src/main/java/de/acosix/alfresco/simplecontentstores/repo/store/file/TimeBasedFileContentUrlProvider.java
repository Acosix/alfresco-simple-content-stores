/*
 * Copyright 2018 Acosix GmbH
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

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.content.filestore.FileContentStore;
import org.alfresco.repo.content.filestore.FileContentUrlProvider;
import org.alfresco.util.GUID;

/**
 * This class duplicates {@link org.alfresco.repo.content.filestore.TimeBasedFileContentUrlProvider} simply due to issues with package
 * method visibility that prevent re-use. The major difference is that this class does not expose static utility methods and allows for a
 * configurable store protocol.
 *
 * Content URL format is <b>store://year/month/day/hour/minute/GUID.bin</b>,
 * but can be configured to include provision for splitting data into
 * buckets within <b>minute</b> range through bucketsPerMinute property :
 * <b>store://year/month/day/hour/minute/bucket/GUID.bin</b> <br>
 * <ul>
 * <li><b>store://</b>: prefix identifying an Alfresco content stores
 * regardless of the persistence mechanism.</li>
 * <li><b>year</b>: year</li>
 * <li><b>month</b>: 1-based month of the year</li>
 * <li><b>day</b>: 1-based day of the month</li>
 * <li><b>hour</b>: 0-based hour of the day</li>
 * <li><b>minute</b>: 0-based minute of the hour</li>
 * <li><b>bucket</b>: 0-based bucket depending second of minute</li>
 * <li><b>GUID</b>: A unique identifier</li>
 * </ul>
 * <p>
 *
 * @author Axel Faust, <a href="http://acosix.de">Acosix GmbH</a>
 * @author Andreea Dragoi (original class)
 */
public class TimeBasedFileContentUrlProvider implements FileContentUrlProvider
{

    protected String storeProtocol = FileContentStore.STORE_PROTOCOL;

    protected int bucketsPerMinute = 0;

    /**
     * @param storeProtocol
     *            the storeProtocol to set
     */
    public void setStoreProtocol(final String storeProtocol)
    {
        this.storeProtocol = storeProtocol;
    }

    /**
     * @param bucketsPerMinute
     *            the bucketsPerMinute to set
     */
    public void setBucketsPerMinute(final int bucketsPerMinute)
    {
        this.bucketsPerMinute = bucketsPerMinute;
    }

    @Override
    public String createNewFileStoreUrl()
    {
        final StringBuilder sb = new StringBuilder(20);
        sb.append(this.storeProtocol);
        sb.append(ContentStore.PROTOCOL_DELIMITER);
        sb.append(createTimeBasedPath(this.bucketsPerMinute));
        sb.append(GUID.generate()).append(".bin");
        return sb.toString();
    }

    protected static String createTimeBasedPath(final int bucketsPerMinute)
    {
        final Calendar calendar = new GregorianCalendar(TimeZone.getDefault(), Locale.ENGLISH);
        final int year = calendar.get(Calendar.YEAR);
        final int month = calendar.get(Calendar.MONTH) + 1;  // 0-based
        final int day = calendar.get(Calendar.DAY_OF_MONTH);
        final int hour = calendar.get(Calendar.HOUR_OF_DAY);
        final int minute = calendar.get(Calendar.MINUTE);
        // create the URL
        final StringBuilder sb = new StringBuilder(20);
        sb.append(year).append('/').append(month).append('/').append(day).append('/').append(hour).append('/').append(minute).append('/');

        if (bucketsPerMinute != 0)
        {
            final long seconds = System.currentTimeMillis() % (60 * 1000);
            final int actualBucket = (int) seconds / ((60 * 1000) / bucketsPerMinute);
            sb.append(actualBucket).append('/');
        }
        // done
        return sb.toString();
    }
}
