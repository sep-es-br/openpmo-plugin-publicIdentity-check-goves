package br.gov.es.pmo.public_identity_check.goves.client;

public final class GovesHttpResponse {

    private final int statusCode;
    private final String body;

    public GovesHttpResponse(final int statusCode, final String body) {
        this.statusCode = statusCode;
        this.body = body;
    }

    public int getStatusCode() {
        return this.statusCode;
    }

    public String getBody() {
        return this.body;
    }
}
