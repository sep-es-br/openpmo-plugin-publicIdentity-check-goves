package br.gov.es.pmo.public_identity_check.goves;

import br.gov.es.pmo.public_identity_check.goves.client.GovesClientTokenProvider;
import br.gov.es.pmo.public_identity_check.goves.client.GovesHttpGateway;
import br.gov.es.pmo.public_identity_check.goves.client.GovesHttpResponse;
import br.gov.es.pmo.public_identity_check.goves.configuration.GovesPublicIdentityProperties;
import br.gov.es.pmo.public_identity_check.model.PublicAgentSearchResult;
import br.gov.es.pmo.public_identity_check.model.PublicIdentityResult;
import br.gov.es.pmo.public_identity_check.model.PublicIdentityStatus;
import br.gov.es.pmo.public_identity_check.model.PublicIdentityType;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GovesPublicIdentityProviderTest {

    private FakeGateway gateway;
    private GovesPublicIdentityProvider provider;

    @Before
    public void setUp() {
        final GovesPublicIdentityProperties properties = new GovesPublicIdentityProperties();
        properties.setAcessoCidadaoBaseUrl("https://acesso.test");
        properties.setOrganizationBaseUrl("https://organization.test");
        this.gateway = new FakeGateway();
        this.provider = new GovesPublicIdentityProvider(
            this.gateway,
            new FixedTokenProvider(),
            properties
        );
    }

    @Test
    public void shouldNotCallPesquisaSubWhenCitizenDoesNotExist() {
        this.gateway.respond("GET", "/api/cidadao/12345678900", 404, "");

        final PublicIdentityResult result = this.provider.findByCpf("123.456.789-00");

        assertEquals(PublicIdentityStatus.NOT_FOUND, result.getStatus());
        assertFalse(this.gateway.wasCalled("PUT", "/api/cidadao/12345678900/pesquisaSub"));
    }

    @Test
    public void shouldReturnOrdinaryCitizenWhenAgentEndpointReturnsNotFound() {
        this.gateway.respond("GET", "/api/cidadao/12345678900", 200, "{}");
        this.gateway.respond("PUT", "/api/cidadao/12345678900/pesquisaSub", 200, "{\"sub\":\"citizen-sub\"}");
        this.gateway.respond("GET", "/api/agentepublico/citizen-sub", 404, "");
        this.gateway.respond("GET", "/api/cidadao/citizen-sub/email", 200,
            "{\"email\":\"citizen@example.com\",\"corporativo\":\"\"}");

        final PublicIdentityResult result = this.provider.findByCpf("12345678900");

        assertEquals(PublicIdentityStatus.FOUND, result.getStatus());
        assertEquals(PublicIdentityType.CITIZEN, result.getType());
        assertEquals("citizen-sub", result.getSub());
        assertEquals("citizen@example.com", result.getEmail());
        assertEquals("citizen", result.getName());
        assertTrue(result.getAssignments().isEmpty());
    }

    @Test
    public void shouldKeepOrdinaryCitizenFoundWhenEmailIsUnavailable() {
        this.gateway.respond("GET", "/api/cidadao/12345678900", 200, "{}");
        this.gateway.respond("PUT", "/api/cidadao/12345678900/pesquisaSub", 200,
            "{\"sub\":\"citizen-sub\"}");
        this.gateway.respond("GET", "/api/agentepublico/citizen-sub", 404, "");
        this.gateway.respond("GET", "/api/cidadao/citizen-sub/email", 503, "");

        final PublicIdentityResult result = this.provider.findByCpf("12345678900");

        assertEquals(PublicIdentityStatus.FOUND, result.getStatus());
        assertEquals(PublicIdentityType.CITIZEN, result.getType());
        assertEquals("citizen-sub", result.getSub());
        assertEquals(null, result.getEmail());
    }

    @Test
    public void shouldLoadAgentRolesAndCacheOrganization() {
        this.gateway.respond("GET", "/api/cidadao/12345678900", 200, "{}");
        this.gateway.respond("PUT", "/api/cidadao/12345678900/pesquisaSub", 200, "{\"sub\":\"agent-sub\"}");
        this.gateway.respond("GET", "/api/agentepublico/agent-sub", 200,
            "{\"Sub\":\"agent-sub\",\"Nome\":\"Maria Silva\",\"Apelido\":\"Maria\",\"Email\":\"maria@example.com\"}");
        this.gateway.respond("GET", "/api/cidadao/agent-sub/email", 200,
            "{\"email\":\"maria@example.com\",\"corporativo\":\"maria@work.example\"}");
        this.gateway.respond("GET", "/api/agentepublico/agent-sub/papeis", 200,
            "[{\"Guid\":\"role-1\",\"Nome\":\"Gestora\",\"Tipo\":\"Cargo\",\"LotacaoGuid\":\"org-1\"},"
                + "{\"Guid\":\"role-2\",\"Nome\":\"Fiscal\",\"Tipo\":\"Papel\",\"LotacaoGuid\":\"org-1\"}]");
        this.gateway.respond("GET", "/organizations/org-1/info", 200,
            "{\"guid\":\"org-1\",\"razaoSocial\":\"Secretaria\",\"nomeFantasia\":\"SEP\",\"sigla\":\"SEP\",\"guidOrganizacaoPai\":\"root\"}");

        final PublicIdentityResult result = this.provider.findByCpf("12345678900");

        assertEquals(PublicIdentityType.PUBLIC_AGENT, result.getType());
        assertEquals(2, result.getAssignments().size());
        assertEquals("SEP", result.getAssignments().get(0).getOrganization().getAbbreviation());
        assertEquals(1, this.gateway.countCalls("GET", "/organizations/org-1/info"));
    }

    @Test
    public void shouldFindAgentsByNameIgnoringAccents() {
        this.gateway.respond("GET", "/api/organizacoes/organograma-operacional", 200,
            "[{\"Guid\":\"root-guid\",\"Sigla\":\"GOVES\",\"Filhos\":[]}]");
        this.gateway.respond("GET",
            "/api/conjunto/root-guid/agentesPublicos?incluirFilhos=true&operacional=true", 200,
            "[{\"Sub\":\"1\",\"Nome\":\"João Silva\"},{\"Sub\":\"2\",\"Nome\":\"Maria Souza\"}]");

        final PublicAgentSearchResult result = this.provider.findPublicAgentsByName("joao");

        assertEquals(PublicIdentityStatus.FOUND, result.getStatus());
        assertEquals(1, result.getAgents().size());
        assertEquals("1", result.getAgents().get(0).getSub());
    }

    private static final class FixedTokenProvider implements GovesClientTokenProvider {
        @Override public String getAcessoCidadaoToken() { return "access-token"; }
        @Override public String getOrganizationToken() { return "organization-token"; }
    }

    private static final class FakeGateway implements GovesHttpGateway {
        private final Map<String, GovesHttpResponse> responses = new HashMap<>();
        private final List<String> calls = new ArrayList<>();

        void respond(final String method, final String path, final int status, final String body) {
            this.responses.put(key(method, path), new GovesHttpResponse(status, body));
        }

        boolean wasCalled(final String method, final String path) {
            return this.calls.contains(key(method, path));
        }

        int countCalls(final String method, final String path) {
            final String expected = key(method, path);
            int count = 0;
            for(final String call : this.calls) if(expected.equals(call)) count++;
            return count;
        }

        @Override
        public GovesHttpResponse exchange(
            final String method,
            final String baseUrl,
            final String path,
            final String accessToken
        ) throws IOException {
            final String key = key(method, path);
            this.calls.add(key);
            final GovesHttpResponse response = this.responses.get(key);
            if(response == null) throw new IOException("Unexpected request: " + key);
            return response;
        }

        private static String key(final String method, final String path) {
            return method.toUpperCase() + " " + path;
        }
    }
}
