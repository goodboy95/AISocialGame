package com.aisocialgame;

import com.aisocialgame.config.AppProperties;
import com.aisocialgame.exception.ApiException;
import com.aisocialgame.integration.consul.ConsulHttpServiceDiscovery;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

class ConsulHttpServiceDiscoveryTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldResolveHealthyInstanceAddress() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/health/service/aienie-userservice-http", exchange ->
                writeJson(exchange, 200, """
                        [{"Node":{"Address":"10.0.0.1"},"Service":{"Address":"192.168.10.20","Port":18080}}]
                        """));
        server.start();

        AppProperties properties = new AppProperties();
        properties.getConsul().setAddress("http://127.0.0.1:" + server.getAddress().getPort());
        ConsulHttpServiceDiscovery discovery = new ConsulHttpServiceDiscovery(properties);

        String address = discovery.resolveHttpAddress("aienie-userservice-http");
        Assertions.assertEquals("http://192.168.10.20:18080", address);
    }

    @Test
    void shouldThrowWhenNoPassingInstance() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/health/service/aienie-userservice-http", exchange ->
                writeJson(exchange, 200, "[]"));
        server.start();

        AppProperties properties = new AppProperties();
        properties.getConsul().setAddress("http://127.0.0.1:" + server.getAddress().getPort());
        ConsulHttpServiceDiscovery discovery = new ConsulHttpServiceDiscovery(properties);

        Assertions.assertThrows(ApiException.class,
                () -> discovery.resolveHttpAddress("aienie-userservice-http"));
    }

    private static void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }
}
