package br.gov.es.pmo.user_a_identify.goves.configuration;

import br.gov.es.pmo.identity_parser.pmo_base.service.ClientCredentialService;
import br.gov.es.pmo.user_a_identify.goves.GovesPublicIdentityProvider;
import br.gov.es.pmo.user_a_identify.goves.client.ApacheGovesHttpGateway;
import br.gov.es.pmo.user_a_identify.goves.client.GovesClientTokenProvider;
import br.gov.es.pmo.user_a_identify.goves.client.GovesHttpGateway;
import br.gov.es.pmo.user_a_identify.goves.client.SpringClientCredentialTokenProvider;
import br.gov.es.pmo.user_a_identify.model.IPublicIdentityProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GovesPublicIdentityProperties.class)
public class GovesPublicIdentityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GovesHttpGateway govesHttpGateway() {
        return new ApacheGovesHttpGateway();
    }

    @Bean
    @ConditionalOnMissingBean
    public GovesClientTokenProvider govesClientTokenProvider(
        final ClientCredentialService clientCredentialService,
        final GovesPublicIdentityProperties properties
    ) {
        return new SpringClientCredentialTokenProvider(clientCredentialService, properties);
    }

    @Bean
    @ConditionalOnMissingBean(IPublicIdentityProvider.class)
    public IPublicIdentityProvider publicIdentityProvider(
        final GovesHttpGateway http,
        final GovesClientTokenProvider tokenProvider,
        final GovesPublicIdentityProperties properties
    ) {
        return new GovesPublicIdentityProvider(http, tokenProvider, properties);
    }
}
