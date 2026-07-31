package br.gov.es.pmo.public_identity_check.goves.client;

public interface GovesClientTokenProvider {

    String getAcessoCidadaoToken();

    String getOrganizationToken();
}
