package br.gov.es.pmo.public_identity_check.goves.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openpmo.public-identity.goves")
public class GovesPublicIdentityProperties {

    private String acessoCidadaoBaseUrl = "https://sistemas.es.gov.br/prodest/acessocidadao.webapi";
    private String organizationBaseUrl = "https://api.organograma.es.gov.br";
    private String registrationId = "idsvr";
    private String organizationRegistrationId = "org";
    private String rootOrganizationAbbreviation = "GOVES";

    public String getAcessoCidadaoBaseUrl() {
        return this.acessoCidadaoBaseUrl;
    }

    public void setAcessoCidadaoBaseUrl(final String acessoCidadaoBaseUrl) {
        this.acessoCidadaoBaseUrl = acessoCidadaoBaseUrl;
    }

    public String getOrganizationBaseUrl() {
        return this.organizationBaseUrl;
    }

    public void setOrganizationBaseUrl(final String organizationBaseUrl) {
        this.organizationBaseUrl = organizationBaseUrl;
    }

    public String getRegistrationId() {
        return this.registrationId;
    }

    public void setRegistrationId(final String registrationId) {
        this.registrationId = registrationId;
    }

    public String getOrganizationRegistrationId() {
        return this.organizationRegistrationId;
    }

    public void setOrganizationRegistrationId(final String organizationRegistrationId) {
        this.organizationRegistrationId = organizationRegistrationId;
    }

    public String getRootOrganizationAbbreviation() {
        return this.rootOrganizationAbbreviation;
    }

    public void setRootOrganizationAbbreviation(final String rootOrganizationAbbreviation) {
        this.rootOrganizationAbbreviation = rootOrganizationAbbreviation;
    }
}
