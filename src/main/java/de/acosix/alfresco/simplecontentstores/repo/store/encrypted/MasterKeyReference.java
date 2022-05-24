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
package de.acosix.alfresco.simplecontentstores.repo.store.encrypted;

import java.io.Serializable;
import java.util.Objects;

import org.alfresco.service.cmr.repository.ContentUrlKey;

/**
 * Instances of this class denote encryption master key identities. Keys may be loaded from more than just one keystore, with potentially
 * overlapping aliases. The Alfresco {@link ContentUrlKey content URL key} supports this via the separate
 * {@link ContentUrlKey#setMasterKeystoreId(String) keystore ID} and {@link ContentUrlKey#setMasterKeyAlias(String) key alias} properties.
 * Therefore all key identities are handled on the same premise within this module using this identity class.
 *
 * @author Axel Faust
 */
public class MasterKeyReference implements Serializable
{

    private static final long serialVersionUID = 3354646638764914487L;

    private final String keystoreId;

    private final String alias;

    /**
     * Constructs a new instance of this class.
     *
     * @param keystoreId
     *            the ID of the keystore from which the key was loaded
     * @param alias
     *            the alias of the key
     */
    public MasterKeyReference(final String keystoreId, final String alias)
    {
        this.keystoreId = keystoreId;
        this.alias = alias;
    }

    /**
     * @return the keystoreId
     */
    public String getKeystoreId()
    {
        return this.keystoreId;
    }

    /**
     * @return the alias
     */
    public String getAlias()
    {
        return this.alias;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode()
    {
        return Objects.hash(this.keystoreId, this.alias);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (obj == null)
        {
            return false;
        }
        if (this.getClass() != obj.getClass())
        {
            return false;
        }
        final MasterKeyReference other = (MasterKeyReference) obj;
        if (this.keystoreId == null)
        {
            if (other.keystoreId != null)
            {
                return false;
            }
        }
        else if (!this.keystoreId.equals(other.keystoreId))
        {
            return false;
        }
        if (this.alias == null)
        {
            if (other.alias != null)
            {
                return false;
            }
        }
        else if (!this.alias.equals(other.alias))
        {
            return false;
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append(this.keystoreId);
        builder.append(":");
        builder.append(this.alias);
        return builder.toString();
    }

}
