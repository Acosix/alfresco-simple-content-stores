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

/* global json: false */

var moduleId = 'acosix-simple-content-stores';

function getMasterKeyManager()
{
    var ctxt, masterKeyManager;
    ctxt = Packages.org.springframework.web.context.ContextLoader.getCurrentWebApplicationContext();
    masterKeyManager = ctxt.getBean(moduleId + '-masterKeyManager',
            Packages.de.acosix.alfresco.simplecontentstores.repo.store.encrypted.MasterKeyManager);
    return masterKeyManager;
}

function getEncryptingContentStoreManager()
{
    var ctxt, encryptingContentStoreManager;
    ctxt = Packages.org.springframework.web.context.ContextLoader.getCurrentWebApplicationContext();
    encryptingContentStoreManager = ctxt.getBean(moduleId + '-encryptingContentStoreManager',
            Packages.de.acosix.alfresco.simplecontentstores.repo.store.encrypted.EncryptingContentStoreManager);
    return encryptingContentStoreManager;
}

function toMasterKeyReference(key)
{
    var keyManager, sepIdx, keystoreId, alias, masterKey;
    
    if (!/^[^:]+:.+$/.test(key))
    {
        status.setCode(status.STATUS_BAD_REQUEST, 'A master key must be specified using the identifer pattern "<keystoreId>:<alias>"');
    }
    else
    {
        keyManager = getMasterKeyManager();
        key = String(key);
        sepIdx = key.indexOf(':');
        keystoreId = key.substring(0, sepIdx);
        alias = key.substring(sepIdx + 1);
        masterKey = new Packages.de.acosix.alfresco.simplecontentstores.repo.store.encrypted.MasterKeyReference(keystoreId, alias);
        return masterKey;
    }
}

function keyEnablement(reqArgs, enable)
{
    var key, keyManager;

    if (reqArgs.length === 0)
    {
        status.setCode(status.STATUS_BAD_REQUEST, 'A master key must be specified');
    }
    else
    {
        key = toMasterKeyReference(reqArgs[0]);
        if (key)
        {
            keyManager = getMasterKeyManager();
            if (enable)
            {
                keyManager.enable(key);
            }
            else
            {
                keyManager.disable(key);
            }
            model.key = key;
        }
    }
}

function countSymmetricKeys(reqArgs)
{
    var keyManager, key;

    keyManager = getMasterKeyManager();
    if (reqArgs.length === 0)
    {
        model.symmetricKeyCounts = keyManager.countEncryptedSymmetricKeys();
    }
    else
    {
        key = toMasterKeyReference(reqArgs[0]);
        if (key)
        {
            model.symmetricKeyCount = keyManager.countEncryptedSymmetricKeys(key);
            model.key = key;
        }
    }
}

function reEncryptSymmetricKeys(reqArgs)
{
    var key, storeManager;

    if (reqArgs.length === 0)
    {
        status.setCode(status.STATUS_BAD_REQUEST, 'A master key must be specified');
    }
    else
    {
        key = toMasterKeyReference(reqArgs[0]);
        if (key)
        {
            storeManager = getEncryptingContentStoreManager();
            storeManager.reEncryptSymmetricKeys(key);
            model.key = key;
        }
    }
}

function main()
{
    var service, reqBody, reqArgs, argIdx, keyManager;

    service = String(url.service);
    model.command = service.substring(service.lastIndexOf('/') + 1);

    // web script json is (unwieldly) org.json.JSONObject
    reqBody = JSON.parse(json.toString());
    reqArgs = [];
    if (reqBody.arguments && Array.isArray(reqBody.arguments))
    {
        for (argIdx = 0; argIdx < reqBody.arguments.length; argIdx++)
        {
            reqArgs[argIdx] = reqBody.arguments[argIdx];
        }
    }

    switch (model.command)
    {
        case 'help': // no-op
            break;
        case 'listEncryptionKeys':
            if (reqArgs.length >= 1 && !/^(in)?active$/.test(reqArgs[0]))
            {
                status.setCode(status.STATUS_BAD_REQUEST, 'Mode parameter may only be "active" or "inactive"');
            }
            else
            {
                keyManager = getMasterKeyManager();
                model.keys = String(reqArgs[0] || 'active') === 'active' ? keyManager.activeKeys : keyManager.disabledKeys;
            }
            break;
        case 'enableEncryptionKey':
            keyEnablement(reqArgs, true);
            break;
        case 'disableEncryptionKey':
            keyEnablement(reqArgs, false);
            break;
        case 'countEncryptedSymmetricKeys':
            countSymmetricKeys(reqArgs);
            break;
        case 'listEncryptionKeysEligibleForReEncryption':
            keyManager = getMasterKeyManager();
            model.keys = keyManager.keysRequiringReEncryption;
            break;
        case 'reEncryptSymmetricKeys':
            reEncryptSymmetricKeys(reqArgs);
            break;
        default:
            status.setCode(status.STATUS_NOT_FOUND, 'Command not found');
    }
}

main();