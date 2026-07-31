package br.gov.es.pmo.public_identity_check.goves.client;

import br.gov.es.pmo.identity_parser.pmo_base.service.ClientCredentialService;
import br.gov.es.pmo.public_identity_check.goves.configuration.GovesPublicIdentityProperties;

public class SpringClientCredentialTokenProvider implements GovesClientTokenProvider {

    private final ClientCredentialService clientCredentialService;
    private final GovesPublicIdentityProperties properties;

    public SpringClientCredentialTokenProvider(
        final ClientCredentialService clientCredentialService,
        final GovesPublicIdentityProperties properties
    ) {
        this.clientCredentialService = clientCredentialService;
        this.properties = properties;
    }

    @Override
    public String getAcessoCidadaoToken() {
        return this.clientCredentialService.getClientToken(this.properties.getRegistrationId());
    }

    @Override
    public String getOrganizationToken() {
        return this.clientCredentialService.getClientToken(
            this.properties.getOrganizationRegistrationId()
        );
    }
}
