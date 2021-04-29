<#compress>
<#--
Copyright 2017 - 2021 Acosix GmbH
 
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
 
http://www.apache.org/licenses/LICENSE-2.0
 
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License. 
 -->
<#escape x as jsonUtils.encodeJSONString(x)>
{
    "preformattedOutputLines": [
        <#switch command>
            <#case "help">
                "help",
                "\t${msg("ootbee-support-tools.command-console.permissions.help.description")}",
                "",
                "listEncryptionKeys <active|disabled>",
                "\t${msg("console-commands.listEncryptionKeys.description")}",
                "",
                "enableEncryptionKey <masterKey>",
                "\t${msg("console-commands.enableEncryptionKey.description")}",
                "",
                "disableEncryptionKey <masterKey>",
                "\t${msg("console-commands.disableEncryptionKey.description")}",
                "",
                "countEncryptedSymmetricKeys <masterKey>",
                "\t${msg("console-commands.countEncryptedSymmetricKeys.description")}",
                "",
                "listEncryptionKeysEligibleForReEncryption",
                "\t${msg("console-commands.listEncryptionKeysEligibleForReEncryption.description")}",
                "",
                "reEncryptSymmetricKeys <masterKey>",
                "\t${msg("console-commands.reEncryptSymmetricKeys.description")}"
                <#break>
            <#case "listEncryptionKeys">
            <#case "listEncryptionKeysEligibleForReEncryption">
                <#if keys?? && keys?size != 0>
                    <#list keys as key>
                        "${msg("console-commands.keyReferenceDetail", key.keystoreId, key.alias)}"<#if key_has_next>,</#if>
                    </#list>
                <#else>
                    "${msg("console-commands.noKeysFound")}"
                </#if>
                <#break>
            <#case "enableEncryptionKey">
                    "${msg("console-commands.enabled", key.keystoreId, key.alias)}"
                <#break>
            <#case "disableEncryptionKey">
                    "${msg("console-commands.disabled", key.keystoreId, key.alias)}"
                <#break>
            <#case "countEncryptedSymmetricKeys">
                <#if symmetricKeyCount?? && key?? && symmetricKeyCount != 0>
                    "${msg("console-commands.keyCount", key.keystoreId, key.alias, symmetricKeyCount)}"
                <#elseif symmetricKeyCounts?? && symmetricKeyCounts?size != 0>
                    <#list symmetricKeyCounts?keys as key>
                        <#-- Note: JS layer transparently applies toString on key -->
                        "${msg("console-commands.keyCount", key?substring(0, key?index_of(":")), key?substring(key?index_of(":") + 1), symmetricKeyCounts[key])}"<#if key_has_next>,</#if>
                    </#list>
                <#else>
                    "${msg("console-commands.zeroKeyCount")}"
                </#if>
                <#break>
            <#case "reEncryptSymmetricKeys">
                    "${msg("console-commands.reEncrypted", key.keystoreId, key.alias)}"
                <#break>
        </#switch>
    ]
}
</#escape>
</#compress>