/**
 * Copyright 2013 Netflix, Inc.
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
package com.netflix.nicobar.manager.explorer;


import javax.annotation.PostConstruct;
import javax.inject.Inject;

import com.netflix.explorers.AbstractExplorerModule;
import com.netflix.explorers.ExplorerManager;
import com.netflix.karyon.spi.Component;

@Component
public class ScriptManagerExplorer extends AbstractExplorerModule {

    private ExplorerManager explorerManager;

    @Inject
    public ScriptManagerExplorer(ExplorerManager manager) {
        super("scriptmanager");
        this.explorerManager = manager;
    }

    @PostConstruct
    public void initialize() {
        super.initialize();
        explorerManager.registerExplorer(this);
    }
}
