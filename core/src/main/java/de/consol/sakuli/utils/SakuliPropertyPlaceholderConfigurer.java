/*
 * Sakuli - Testing and Monitoring-Tool for Websites and common UIs.
 *
 * Copyright 2013 - 2014 the original author or authors.
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

package de.consol.sakuli.utils;

import de.consol.sakuli.datamodel.properties.SahiProxyProperties;
import de.consol.sakuli.datamodel.properties.SakuliProperties;
import de.consol.sakuli.datamodel.properties.TestSuiteProperties;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

/**
 * Overrides the default {@link PropertyPlaceholderConfigurer} to dynamically load the properties files in  the {@link TestSuiteProperties#TEST_SUITE_FOLDER}
 * and {@link SakuliProperties#INCLUDE_FOLDER}.
 *
 * @author tschneck
 *         Date: 11.05.14
 */
public class SakuliPropertyPlaceholderConfigurer extends PropertyPlaceholderConfigurer {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    public static String TEST_SUITE_FOLDER_VALUE;
    public static String INCLUDE_FOLDER_VALUE;
    public static String SAHI_PROXY_HOME_VALUE;
    private boolean loadSakuliProperties = true;
    private boolean loadTestSuiteProperties = true;
    private boolean writePropertiesToSahiConfig = true;
    private Map<String, Map<String, Object>> modifiedSahiConfigProps;

    public SakuliPropertyPlaceholderConfigurer() {
        modifiedSahiConfigProps = new HashMap<>();
    }

    @Override
    protected void loadProperties(Properties props) throws IOException {
        //load properties set by command args
        props.put(TestSuiteProperties.TEST_SUITE_FOLDER, TEST_SUITE_FOLDER_VALUE);
        props.put(SakuliProperties.INCLUDE_FOLDER, INCLUDE_FOLDER_VALUE);

        //load common sakuli properties
        String sakuliProperties = Paths.get(INCLUDE_FOLDER_VALUE).normalize().toAbsolutePath().toString() + SakuliProperties.SAKULI_PROPERTIES_FILE_APPENDER;
        addPropertiesFromFile(props, sakuliProperties, loadSakuliProperties);
        String testSuitePropFile = Paths.get(TEST_SUITE_FOLDER_VALUE).normalize().toAbsolutePath().toString() + TestSuiteProperties.TEST_SUITE_PROPERTIES_FILE_APPENDER;
        addPropertiesFromFile(props, testSuitePropFile, loadTestSuiteProperties);
        //override if set sahi proxy home
        if (StringUtils.isNotEmpty(SAHI_PROXY_HOME_VALUE)) {
            props.setProperty(SahiProxyProperties.PROXY_HOME_FOLDER, SAHI_PROXY_HOME_VALUE);
        }
        modifySahiProperties(props);
        super.loadProperties(props);
    }

    @PreDestroy
    public void restoreProperties() {
        try {
            for (Map.Entry<String, Map<String, Object>> entry : modifiedSahiConfigProps.entrySet()) {
                String propFile = entry.getKey();
                logger.debug("restore properties file '{}' with properties '{}'", propFile, entry.getValue());
                PropertiesConfiguration propConfig = new PropertiesConfiguration(propFile);
                propConfig.setAutoSave(true);
                for (Map.Entry<String, Object> propEntry : entry.getValue().entrySet()) {
                    String propKey = propEntry.getKey();
                    if (propConfig.containsKey(propKey)) {
                        propConfig.clearProperty(propKey);
                    }
                    propConfig.addProperty(propKey, propEntry.getValue());
                }
            }
        } catch (ConfigurationException e) {
            logger.error("Error in restore sahi config properties", e);
        }
    }

    /**
     * Reads in the properties for a specific file
     *
     * @param props    Properties to update
     * @param filePath path to readable properties file
     * @param active   activate or deactivate the  function
     */
    protected void addPropertiesFromFile(Properties props, String filePath, boolean active) {
        if (active) {
            logger.info("read in properties from '{}'", filePath);
            try {
                PropertiesConfiguration propertiesConfiguration = new PropertiesConfiguration(filePath);
                Iterator<String> keyIt = propertiesConfiguration.getKeys();
                while (keyIt.hasNext()) {
                    String key = keyIt.next();
                    Object value = propertiesConfiguration.getProperty(key);
                    props.put(key, value);
                }
            } catch (ConfigurationException | NullPointerException e) {
                throw new RuntimeException("Error by reading the property file '" + filePath + "'", e);
            }
        }
    }

    protected void modifySahiProperties(Properties props) {
        //TODO TS write unit test!
        if (writePropertiesToSahiConfig) {
            String sahiConfigFolerPath = resolve(props.getProperty(SahiProxyProperties.PROXY_CONFIG_FOLDER), props);

            String sahiPropConfig = Paths.get(sahiConfigFolerPath + SahiProxyProperties.SAHI_PROPERTY_FILE_APPENDER).normalize().toString();
            modifyPropertiesConfiguration(sahiPropConfig, SahiProxyProperties.userdataPropertyNames, props);
            String sahiLogPropConfig = Paths.get(sahiConfigFolerPath + SahiProxyProperties.SAHI_LOG_PROPERTY_FILE_APPENDER).normalize().toString();
            modifyPropertiesConfiguration(sahiLogPropConfig, SahiProxyProperties.logPropertyNames, props);
        }
    }

    private String resolve(String string, Properties props) {
        if (string != null) {
            int i = string.indexOf("${");
            if (i >= 0) {
                String result = string.substring(0, i);
                int iLast = string.indexOf("}");
                String key = string.substring(i + 2, iLast);
                result += props.get(key) + string.substring(iLast + 1);
                return resolve(result, props);
            }
        }
        return string;
    }

    /**
     * Modifies the properties file 'propFilePathToConfig' with assigned key from the resource properties.
     */
    protected void modifyPropertiesConfiguration(String propFilePathToConfig, List<String> updateKeys, Properties resourceProps) {
        try {
            PropertiesConfiguration propConfig = new PropertiesConfiguration(propFilePathToConfig);
            propConfig.setAutoSave(true);
            Properties temProps = new Properties();
            for (String propKey : updateKeys) {
                String resolve = resolve(resourceProps.getProperty(propKey), resourceProps);
                if (resolve != null) {
                    if (propConfig.containsKey(propKey)) {
                        addToModifiedPropertiesMap(propFilePathToConfig, propKey, propConfig.getProperty(propKey));
                        propConfig.clearProperty(propKey);
                    }
                    temProps.put(propKey, resolve);
                    propConfig.addProperty(propKey, resolve);
                }
            }
            logger.debug("modify properties file '{}' with '{}'", propFilePathToConfig, temProps.toString());
        } catch (ConfigurationException e) {
            throw new RuntimeException("Error by reading the property file '" + propFilePathToConfig + "'", e);
        }

    }

    protected void addToModifiedPropertiesMap(String propFilePath, String propKey, Object propertyValue) {
        Map<String, Object> propMap = modifiedSahiConfigProps.get(propFilePath);
        if (propMap == null) {
            propMap = new HashMap<>();
        }
        propMap.put(propKey, propertyValue);
        modifiedSahiConfigProps.put(propFilePath, propMap);
    }

    public boolean isLoadSakuliProperties() {
        return loadSakuliProperties;
    }

    public void setLoadSakuliProperties(boolean loadSakuliProperties) {
        this.loadSakuliProperties = loadSakuliProperties;
    }

    public boolean isLoadTestSuiteProperties() {
        return loadTestSuiteProperties;
    }

    public void setLoadTestSuiteProperties(boolean loadTestSuiteProperties) {
        this.loadTestSuiteProperties = loadTestSuiteProperties;
    }

    public boolean isWritePropertiesToSahiConfig() {
        return writePropertiesToSahiConfig;
    }

    public void setWritePropertiesToSahiConfig(boolean writePropertiesToSahiConfig) {
        this.writePropertiesToSahiConfig = writePropertiesToSahiConfig;
    }
}