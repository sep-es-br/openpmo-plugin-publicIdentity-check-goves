package br.gov.es.pmo.user_a_identify.goves.client;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;

public class ApacheGovesHttpGateway implements GovesHttpGateway {

    @Override
    public GovesHttpResponse exchange(
        final String method,
        final String baseUrl,
        final String path,
        final String accessToken
    ) throws IOException {
        final HttpUriRequest request = "PUT".equalsIgnoreCase(method)
            ? new HttpPut(join(baseUrl, path))
            : new HttpGet(join(baseUrl, path));

        request.addHeader("Authorization", "Bearer " + accessToken);
        request.addHeader("Accept", "application/json");

        try(final CloseableHttpClient client = HttpClients.createDefault();
            final CloseableHttpResponse response = client.execute(request)) {
            final String body = response.getEntity() == null
                ? ""
                : EntityUtils.toString(response.getEntity());
            return new GovesHttpResponse(response.getStatusLine().getStatusCode(), body);
        }
    }

    private static String join(final String baseUrl, final String path) {
        if(baseUrl.endsWith("/") && path.startsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + path;
        }
        if(!baseUrl.endsWith("/") && !path.startsWith("/")) {
            return baseUrl + "/" + path;
        }
        return baseUrl + path;
    }
}
