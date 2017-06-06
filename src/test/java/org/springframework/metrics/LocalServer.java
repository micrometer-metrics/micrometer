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
