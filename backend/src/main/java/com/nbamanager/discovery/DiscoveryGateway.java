package com.nbamanager.discovery;

import com.nbamanager.exception.ApiException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class DiscoveryGateway {
    private final RestClient client;
    private final RestClient recommendationClient;
    private final String token;
    private final int timeout;
    private final DiscoverySuggestionCache suggestions=new DiscoverySuggestionCache();
    public DiscoveryGateway(@Value("${app.discovery.server-url}") String url,
                            @Value("${app.discovery.recommendation-server-url:}") String recommendationUrl,
                            @Value("${app.discovery.internal-token:}") String token,
                            @Value("${app.discovery.timeout-millis:800}") int timeout) {
        this.token=token;
        this.timeout=timeout;
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(150)).build());
        factory.setReadTimeout(Duration.ofMillis(timeout));
        client=RestClient.builder().baseUrl(url).requestFactory(factory).build();
        recommendationClient=recommendationUrl.isBlank()?client:
                RestClient.builder().baseUrl(recommendationUrl).requestFactory(factory).build();
    }
    public Map<String,Object> fetch(String operation, Map<String,Object> request) {
        if(token.isBlank()) return null;
        if(operation.equals("suggest")&&!request.containsKey("cursor"))
            return suggestions.get(request,timeout,()->call(operation,request));
        return call(operation,request);
    }
    private Map<String,Object> call(String operation, Map<String,Object> request) {
        try {
            RestClient target=operation.equals("recommendations")?recommendationClient:client;
            return target.post().uri("/internal/"+operation).header("X-Discovery-Token",token)
                    .body(request).retrieve().body(new ParameterizedTypeReference<>() {});
        } catch(RestClientResponseException error) {
            if(error.getStatusCode().value()==410) throw new ApiException(HttpStatus.GONE,"列表已过期，请刷新后重试");
            if(error.getStatusCode().value()==422) throw DiscoveryTypes.bad("搜索参数无效");
            return null;
        } catch(RestClientException error) { return null; }
    }
}
