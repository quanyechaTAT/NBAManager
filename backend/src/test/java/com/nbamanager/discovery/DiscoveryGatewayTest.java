package com.nbamanager.discovery;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class DiscoveryGatewayTest {
    HttpServer server;
    ExecutorService serverThreads;
    DiscoveryGateway gateway;
    AtomicInteger calls;
    String response;

    @BeforeEach void start() throws Exception {
        calls=new AtomicInteger();
        response="{\"items\":[{\"itemKey\":\"NEWS:1\",\"score\":0.5}],\"nextCursor\":null,\"degraded\":false,\"strategyVersion\":\"bm25-v1\"}";
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        serverThreads=Executors.newCachedThreadPool(); server.setExecutor(serverThreads);
        server.createContext("/internal/",exchange->{
            calls.incrementAndGet(); exchange.getRequestBody().readAllBytes();
            byte[] body=response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,body.length);
            try(var output=exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        gateway=new DiscoveryGateway("http://127.0.0.1:"+server.getAddress().getPort(),"test-only",800);
    }
    @AfterEach void stop() { server.stop(0); serverThreads.shutdownNow(); }
    Map<String,Object> request(long user,String q) {
        return Map.of("userId",user,"scene","SEARCH","q",q,"limit",8,"types",List.of());
    }

    @Test void concurrentSuggestionsShareOnlyRetrievalAcrossUsers() throws Exception {
        var clients=Executors.newFixedThreadPool(12);
        try {
            var start=new CountDownLatch(1);
            List<Future<Map<String,Object>>> requests=new ArrayList<>();
            for(int i=0;i<12;i++) {
                long user=i+1;
                requests.add(clients.submit(()->{ start.await(); return gateway.fetch("suggest",request(user,"库里")); }));
            }
            start.countDown();
            for(var result:requests) assertThat(result.get(2,TimeUnit.SECONDS)).containsEntry("degraded",false);
            assertThat(calls.get()).isEqualTo(1);
            gateway.fetch("suggest",request(1,"湖人"));
            assertThat(calls.get()).isEqualTo(2);
        } finally { clients.shutdownNow(); }
    }

    @Test void personalizedAndCursorResponsesAreNeverShared() {
        gateway.fetch("recommendations",request(1,"库里"));
        gateway.fetch("recommendations",request(1,"库里"));
        response=response.replace("\"nextCursor\":null","\"nextCursor\":\"private:8\"");
        gateway.fetch("suggest",request(1,"库里"));
        gateway.fetch("suggest",request(2,"库里"));
        assertThat(calls.get()).isEqualTo(4);
    }
}
