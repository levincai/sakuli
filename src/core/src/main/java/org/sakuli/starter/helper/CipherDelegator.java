/*
 * Sakuli - Testing and Monitoring-Tool for Websites and common UIs.
 *
 * Copyright 2013 - 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sakuli.starter.helper;

import org.sakuli.datamodel.properties.CipherProperties;
import org.sakuli.exceptions.SakuliCipherException;
import org.sakuli.services.cipher.NetworkInterfaceCipher;

import java.util.AbstractMap;
import java.util.Map;

import static org.apache.commons.lang.StringUtils.isNotEmpty;

/**
 * Helper class to delegate which cipher implementation should be used.
 *
 * @author tschneck
 *         Date: 6/28/17
 */
public class CipherDelegator {
    /**
     * @param strToEncrypt
     * @param ethInterface
     * @return a entry with "secret, generation-info"
     * @throws SakuliCipherException
     */
    public static Map.Entry<String, String> encrypt(String strToEncrypt, String ethInterface) throws SakuliCipherException {
        CipherProperties cipherProps = new CipherProperties();
        if (isNotEmpty(ethInterface)) {
            //TODO TS USE keybased
            cipherProps.setEncryptionInterface(ethInterface);
            cipherProps.setEncryptionInterfaceAutodetect(false);
        } else {
            cipherProps.setEncryptionInterfaceAutodetect(true);
        }
        NetworkInterfaceCipher cipher = new NetworkInterfaceCipher(cipherProps);
        cipher.scanNetworkInterfaces();
        return new AbstractMap.SimpleEntry<>("interface " + cipher.getInterfaceName(), cipher.encrypt(strToEncrypt));
    }
}