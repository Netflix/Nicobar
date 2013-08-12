/*
 *
 *  Copyright 2013 Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */
package com.netflix.scriptlib.core;

import java.util.Map;

import org.apache.commons.lang.Validate;

import com.netflix.scriptlib.core.module.JBossScriptModule;

/**
 * Operational metadata pertaining to a {@link JBossScriptModule}.
 *
 * @author James Kojo
 */
public class ScriptModuleOperationalData {
    public static class Builder {
        private final String moduleName;
        private final int moduleVersion;

        private double allocation;
        private Map<String, String> applicationData;
        private long createTime;
        private long lastModifiedTime;
        public Builder(String moduleName, int moduleVersion) {
            this.moduleName = moduleName;
            this.moduleVersion = moduleVersion;
        }
        public Builder setAllocation(double allocation) {
            this.allocation = allocation;
            return this;
        }
        public Builder setApplicationData(Map<String, String> applicationData) {
            this.applicationData = applicationData;
            return this;
        }
        public Builder setCreateTime(long createTime) {
            this.createTime = createTime;
            return this;
        }
        public Builder setLastModifiedTime(long lastModifiedTime) {
            this.lastModifiedTime = lastModifiedTime;
            return this;
        }
        public ScriptModuleOperationalData build() {
            return new ScriptModuleOperationalData(moduleName, moduleVersion, allocation, applicationData, createTime, lastModifiedTime);
        }
    }

    private final String moduleName;
    private final int moduleVersion;
    private final double allocation;
    private final Map<String, String> applicationData;
    private final long createTime;
    private final long lastModifiedTime;
    private ScriptModuleOperationalData(String moduleName, int moduleVersion, double allocation,
        Map<String, String> applicationData, long createTime, long lastModifiedTime) {
        Validate.notEmpty(moduleName, "moduleName");
        Validate.isTrue(moduleVersion >= 0, "moduleVersion");
        this.moduleName = moduleName;
        this.moduleVersion = moduleVersion;
        this.allocation = allocation;
        this.applicationData = applicationData;
        this.createTime = createTime;
        this.lastModifiedTime = lastModifiedTime;
    }
    public String getModuleName() {
        return moduleName;
    }
    public int getModuleVersion() {
        return moduleVersion;
    }
    public double getAllocation() {
        return allocation;
    }
    public Map<String, String> getApplicationData() {
        return applicationData;
    }
    public long getCreateTime() {
        return createTime;
    }
    public long getLastModifiedTime() {
        return lastModifiedTime;
    }
}
