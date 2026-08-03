package br.gov.es.pmo.user_a_identify.goves.client;

import java.io.IOException;

public interface GovesHttpGateway {

    GovesHttpResponse exchange(
        String method,
        String baseUrl,
        String path,
        String accessToken
    ) throws IOException;
}
