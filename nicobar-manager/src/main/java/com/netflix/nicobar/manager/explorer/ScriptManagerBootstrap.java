/*
 * Copyright 2013 Netflix, Inc.
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
 */
package com.netflix.nicobar.manager.explorer;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Scopes;
import com.google.inject.name.Names;
import com.netflix.explorers.AppConfigGlobalModelContext;
import com.netflix.explorers.ExplorerManager;
import com.netflix.explorers.ExplorersManagerImpl;
import com.netflix.explorers.context.GlobalModelContext;
import com.netflix.governator.guice.LifecycleInjectorBuilder;
import com.netflix.karyon.server.ServerBootstrap;
import com.netflix.nicobar.manager.rest.GsonMessageBodyHandler;
import com.sun.jersey.api.core.PackagesResourceConfig;
import com.sun.jersey.guice.JerseyServletModule;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;

public class ScriptManagerBootstrap extends ServerBootstrap {
    private static final Logger LOG = LoggerFactory.getLogger(ScriptManagerBootstrap.class);

    @Override
    protected void beforeInjectorCreation(@SuppressWarnings("unused") LifecycleInjectorBuilder builderToBeUsed) {

       JerseyServletModule jerseyServletModule = new JerseyServletModule() {
            @Override
            protected void configureServlets() {
                bind(String.class).annotatedWith(Names.named("explorerAppName")).toInstance("scriptmanager");
                bind(GsonMessageBodyHandler.class).in(Scopes.SINGLETON);
                bind(GlobalModelContext.class).to(AppConfigGlobalModelContext.class);
                bind(ExplorerManager.class).to(ExplorersManagerImpl.class);
                bind(ScriptManagerExplorer.class);

                bind(GuiceContainer.class).asEagerSingleton();

                Map<String, String> params = new HashMap<String, String>();
                params.put(PackagesResourceConfig.PROPERTY_PACKAGES,
                    // pytheas resources
                    "com.netflix.explorers.resources;" +
                    "com.netflix.explorers.providers;" +
                    // nicobar resources
                    "com.netflix.nicobar.manager.explorer.resources");

                // Route all requests through GuiceContainer
                serve("/*").with(GuiceContainer.class, params);
            }
        };
        builderToBeUsed.withAdditionalModules(jerseyServletModule);
        LOG.debug("HelloWorldBootstrap injected jerseyServletModule in LifecycleInjectorBuilder");
    }
}
