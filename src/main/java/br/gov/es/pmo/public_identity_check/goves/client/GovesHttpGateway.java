package br.gov.es.pmo.public_identity_check.goves.client;

import java.io.IOException;

public interface GovesHttpGateway {

    GovesHttpResponse exchange(
        String method,
        String baseUrl,
        String path,
        String accessToken
    ) throws IOException;
}
