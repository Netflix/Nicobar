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
package com.netflix.scriptlib.core.module;

import javax.annotation.Nullable;

import com.netflix.scriptlib.core.archive.ScriptArchive;

/**
 * Listener for new/updated or deleted modules.
 *
 * @author James Kojo
 */
public interface ScriptModuleListener {

    /**
     * Notification that a module was newly created or updated.
     * @param newScriptModule newly loaded version of the module. NULL if the module has been deleted.
     * @param oldScriptModule old version of the module that will be unloaded. NULL if this is a new module.
     */
    void moduleUpdated(@Nullable ScriptModule newScriptModule, @Nullable ScriptModule oldScriptModule);

    /**
     * Notification that a script archive was rejected by the module loader
     * @param scriptArchive archive that was rejected
     * @param reason reason it was rejected
     * @param cause underlying exception which triggered the rejection
     */
    void archiveRejected(ScriptArchive scriptArchive, ArchiveRejectedReason reason, @Nullable Throwable cause);
}
