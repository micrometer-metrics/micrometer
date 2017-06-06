/**
 * Copyright 2017 Pivotal Software, Inc.
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
package org.springframework.metrics;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ServletHttpHandlerAdapter;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import javax.servlet.Servlet;

public class LocalServer {
    public static Tomcat tomcatServer(int port, RouterFunction<ServerResponse> route) {
        HttpHandler httpHandler = RouterFunctions.toHttpHandler(route);
        Servlet servlet = new ServletHttpHandlerAdapter(httpHandler);
        Tomcat server = new Tomcat();
        server.setPort(port);
        Context rootContext = server.addContext("", System.getProperty("java.io.tmpdir"));
        Tomcat.addServlet(rootContext, "servlet", servlet);
        rootContext.addServletMappingDecoded("/", "servlet");
        try {
            server.start();
        } catch (LifecycleException e) {
            throw new RuntimeException(e);
        }
        return server;
    }
}
