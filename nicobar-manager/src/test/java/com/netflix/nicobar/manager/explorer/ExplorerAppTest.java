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


import static org.testng.Assert.assertEquals;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.google.inject.servlet.GuiceFilter;
import com.netflix.karyon.server.guice.KaryonGuiceContextListener;

public class ExplorerAppTest {
    private static final Logger LOG = LoggerFactory.getLogger(ExplorerAppTest.class);


    private static final Map<String, String> REST_END_POINTS = new ImmutableMap.Builder<String, String>()
        .put("/", MediaType.TEXT_HTML)
        .put("/scriptmanager", MediaType.TEXT_HTML)
        .put("/scriptmanager/repositorysummaries", MediaType.APPLICATION_JSON)
        .build();

    private static int TEST_LOCAL_PORT;

    static {
        try {
            TEST_LOCAL_PORT = getLocalPort();
        } catch (IOException e) {
            LOG.error("IOException in finding local port for starting jetty ", e);
        }
    }


    private static int getLocalPort() throws IOException {
        ServerSocket ss = new ServerSocket(0);
        ss.setReuseAddress(true);
        return ss.getLocalPort();
    }

    private Server server;

    @BeforeClass
    public void init() throws Exception {
        System.setProperty("archaius.deployment.applicationId","scriptmanager-app");
        System.setProperty("archaius.deployment.environment","dev");

        server = new Server(TEST_LOCAL_PORT);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        context.addEventListener(new KaryonGuiceContextListener());

        context.addFilter(GuiceFilter.class, "/*", 1);
        context.addServlet(DefaultServlet.class, "/");

        server.setHandler(context);

        server.start();
    }

    @AfterClass
    public void cleanup() throws Exception {
        if (server != null) {
            server.stop();
        }
    }


    @Test
    public void verifyRESTEndpoints() throws IOException {
        HttpClient client = new DefaultHttpClient();
        for (Map.Entry<String, String> restEndPoint : REST_END_POINTS.entrySet()) {
            final String endPoint = buildLocalHostEndpoint(restEndPoint.getKey());
            LOG.info("REST endpoint " + endPoint);
            HttpGet restGet = new HttpGet(endPoint);
            HttpResponse response = client.execute(restGet);
            assertEquals(response.getStatusLine().getStatusCode(), 200);
            assertEquals(response.getEntity().getContentType().getValue(), restEndPoint.getValue());

            // need to consume full response before make another rest call with
            // the default SingleClientConnManager used with DefaultHttpClient
            IOUtils.closeQuietly(response.getEntity().getContent());

        }
    }

    private String buildLocalHostEndpoint(String endPoint) {
        return "http://localhost:" + TEST_LOCAL_PORT + endPoint;
    }

}
