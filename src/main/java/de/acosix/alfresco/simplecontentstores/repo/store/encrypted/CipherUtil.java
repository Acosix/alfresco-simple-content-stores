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

import java.security.GeneralSecurityException;
import java.security.Key;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

import org.alfresco.util.ParameterCheck;

/**
 * @author Axel Faust
 */
public class CipherUtil
{

    private static final Map<String, String> PADDINGS_BY_ALGORITHM;
    static
    {
        // paddings required to be supported by Java Security specs
        final Map<String, String> paddings = new HashMap<>();

        paddings.put("AES", "CBC/PKCS5Padding");
        paddings.put("DES", "CBC/PKCS5Padding");
        paddings.put("DESede", "CBC/PKCS5Padding");
        // in previous versions we hard-coded appended "/CBC/PKCS5Padding" to any algorithm, including RSA
        // tests show that apparently this suffix was ignored in previous versions, and existing RSA encrypted symmetric keys are
        // decryptable with the following padding
        paddings.put("RSA", "ECB/OAEPWithSHA-256AndMGF1Padding");

        PADDINGS_BY_ALGORITHM = Collections.unmodifiableMap(paddings);
    }

    private CipherUtil()
    {
        // NO-OP
    }

    protected static Cipher getInitialisedCipher(final Key key, final boolean encrypt) throws GeneralSecurityException
    {
        ParameterCheck.mandatory("key", key);

        String algorithm = key.getAlgorithm();
        Cipher cipher = Cipher.getInstance(algorithm);
        if (cipher.getBlockSize() == 0)
        {
            cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, key);
        }
        else
        {
            if (PADDINGS_BY_ALGORITHM.containsKey(algorithm))
            {
                algorithm = algorithm + "/" + PADDINGS_BY_ALGORITHM.get(algorithm);
            }
            cipher = Cipher.getInstance(algorithm);
            // no way to record/transport iv for each key in Alfresco (also symmetric keys are only used for one encryption)
            cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, key, new IvParameterSpec(new byte[cipher.getBlockSize()]));
        }
        return cipher;
    }
}
