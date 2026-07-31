package br.gov.es.pmo.public_identity_check.goves;

import br.gov.es.pmo.public_identity_check.goves.client.GovesClientTokenProvider;
import br.gov.es.pmo.public_identity_check.goves.client.GovesHttpGateway;
import br.gov.es.pmo.public_identity_check.goves.client.GovesHttpResponse;
import br.gov.es.pmo.public_identity_check.goves.configuration.GovesPublicIdentityProperties;
import br.gov.es.pmo.public_identity_check.model.IPublicIdentityProvider;
import br.gov.es.pmo.public_identity_check.model.OrganizationInfo;
import br.gov.es.pmo.public_identity_check.model.PublicAgentAssignment;
import br.gov.es.pmo.public_identity_check.model.PublicAgentSearchResult;
import br.gov.es.pmo.public_identity_check.model.PublicAgentSummary;
import br.gov.es.pmo.public_identity_check.model.PublicIdentityResult;
import br.gov.es.pmo.public_identity_check.model.PublicIdentityType;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GovesPublicIdentityProvider implements IPublicIdentityProvider {

    private static final int HTTP_OK = 200;
    private static final int HTTP_NOT_FOUND = 404;

    private final GovesHttpGateway http;
    private final GovesClientTokenProvider tokenProvider;
    private final GovesPublicIdentityProperties properties;
    private final Map<String, OrganizationInfo> organizationCache = new ConcurrentHashMap<>();

    public GovesPublicIdentityProvider(
        final GovesHttpGateway http,
        final GovesClientTokenProvider tokenProvider,
        final GovesPublicIdentityProperties properties
    ) {
        this.http = http;
        this.tokenProvider = tokenProvider;
        this.properties = properties;
    }

    @Override
    public PublicIdentityResult findByCpf(final String cpf) {
        try {
            final String token = this.tokenProvider.getAcessoCidadaoToken();
            final GovesHttpResponse citizen = this.http.exchange(
                "GET",
                this.properties.getAcessoCidadaoBaseUrl(),
                "/api/cidadao/" + cpf,
                token
            );

            if(citizen.getStatusCode() == HTTP_NOT_FOUND) {
                return PublicIdentityResult.notFound(cpf);
            }
            if(citizen.getStatusCode() != HTTP_OK) {
                return PublicIdentityResult.unavailable(cpf);
            }

            final GovesHttpResponse subResponse = this.http.exchange(
                "PUT",
                this.properties.getAcessoCidadaoBaseUrl(),
                "/api/cidadao/" + cpf + "/pesquisaSub",
                token
            );
            if(subResponse.getStatusCode() != HTTP_OK) {
                return PublicIdentityResult.unavailable(cpf);
            }

            final JSONObject subJson = new JSONObject(subResponse.getBody());
            final String sub = value(subJson, "sub", "Sub");
            if(isBlank(sub)) {
                return PublicIdentityResult.unavailable(cpf);
            }

            final GovesHttpResponse rolesResponse = this.getPublicAgentRoles(sub, token);
            if(rolesResponse.getStatusCode() == HTTP_NOT_FOUND) {
                return this.loadCitizen(cpf, sub, token);
            }
            if(rolesResponse.getStatusCode() != HTTP_OK) {
                return PublicIdentityResult.unavailable(cpf);
            }
            final JSONObject prioritizedRole = selectPrioritizedRole(
                new JSONArray(rolesResponse.getBody())
            );
            if(prioritizedRole == null) {
                return this.loadCitizen(cpf, sub, token);
            }
            return this.loadPublicAgent(cpf, sub, prioritizedRole, token);
        }
        catch(final RuntimeException | IOException e) {
            return PublicIdentityResult.unavailable(cpf);
        }
    }

    @Override
    public PublicAgentSearchResult findPublicAgentsByName(final String name) {
        if(isBlank(name)) {
            return PublicAgentSearchResult.found(Collections.emptyList());
        }

        try {
            final String token = this.tokenProvider.getAcessoCidadaoToken();
            final GovesHttpResponse organizations = this.http.exchange(
                "GET",
                this.properties.getAcessoCidadaoBaseUrl(),
                "/api/organizacoes/organograma-operacional",
                token
            );
            if(organizations.getStatusCode() != HTTP_OK) {
                return PublicAgentSearchResult.unavailable();
            }

            final String organizationGuid = this.findOrganizationGuid(
                new JSONArray(organizations.getBody()),
                this.properties.getRootOrganizationAbbreviation()
            );
            if(isBlank(organizationGuid)) {
                return PublicAgentSearchResult.unavailable();
            }

            final GovesHttpResponse agentsResponse = this.http.exchange(
                "GET",
                this.properties.getAcessoCidadaoBaseUrl(),
                "/api/conjunto/" + organizationGuid
                    + "/agentesPublicos?incluirFilhos=true&operacional=true",
                token
            );
            if(agentsResponse.getStatusCode() != HTTP_OK) {
                return PublicAgentSearchResult.unavailable();
            }

            final String normalizedName = normalizeText(name);
            final List<PublicAgentSummary> agents = new ArrayList<>();
            final JSONArray array = new JSONArray(agentsResponse.getBody());
            for(int i = 0; i < array.length(); i++) {
                final JSONObject item = array.optJSONObject(i);
                if(item == null) continue;
                final String agentName = value(item, "Nome", "nome");
                final String sub = value(item, "Sub", "sub");
                if(!isBlank(agentName)
                    && !isBlank(sub)
                    && normalizeText(agentName).contains(normalizedName)) {
                    agents.add(new PublicAgentSummary(sub, agentName));
                }
            }
            agents.sort(Comparator.comparing(PublicAgentSummary::getName, String.CASE_INSENSITIVE_ORDER));
            return PublicAgentSearchResult.found(agents);
        }
        catch(final RuntimeException | IOException e) {
            return PublicAgentSearchResult.unavailable();
        }
    }

    @Override
    public PublicIdentityResult findPublicAgentBySub(final String sub) {
        if(isBlank(sub)) {
            return PublicIdentityResult.notFound(null);
        }
        try {
            final String token = this.tokenProvider.getAcessoCidadaoToken();
            final GovesHttpResponse rolesResponse = this.getPublicAgentRoles(sub, token);
            if(rolesResponse.getStatusCode() == HTTP_NOT_FOUND) {
                return PublicIdentityResult.notFound(null);
            }
            if(rolesResponse.getStatusCode() != HTTP_OK) {
                return PublicIdentityResult.unavailable(null);
            }
            final JSONObject prioritizedRole = selectPrioritizedRole(
                new JSONArray(rolesResponse.getBody())
            );
            if(prioritizedRole == null) {
                return PublicIdentityResult.notFound(null);
            }
            return this.loadPublicAgent(null, sub, prioritizedRole, token);
        }
        catch(final RuntimeException | IOException e) {
            return PublicIdentityResult.unavailable(null);
        }
    }

    private GovesHttpResponse getPublicAgentRoles(final String sub, final String token) throws IOException {
        return this.http.exchange(
            "GET",
            this.properties.getAcessoCidadaoBaseUrl(),
            "/api/agentepublico/" + sub + "/papeis",
            token
        );
    }

    private PublicIdentityResult loadCitizen(
        final String cpf,
        final String sub,
        final String token
    ) {
        final JSONObject emailJson = this.loadEmailIfAvailable(sub, token);
        final String email = emailJson == null
            ? null
            : value(emailJson, "email", "Email");
        final String corporateEmail = emailJson == null
            ? null
            : value(emailJson, "corporativo", "Corporativo");
        final String name = nameFromEmail(email);
        return PublicIdentityResult.found(
            PublicIdentityType.CITIZEN,
            cpf,
            sub,
            name,
            name,
            email,
            corporateEmail,
            Collections.emptyList()
        );
    }

    private PublicIdentityResult loadPublicAgent(
        final String cpf,
        final String requestedSub,
        final JSONObject prioritizedRole,
        final String token
    ) throws IOException {
        final String sub = firstNotBlank(
            value(prioritizedRole, "AgentePublicoSub", "agentePublicoSub"),
            requestedSub
        );

        final JSONObject email = this.loadEmailIfAvailable(sub, token);
        final PublicAgentAssignment assignment = this.mapAssignment(prioritizedRole);
        if(assignment == null) {
            return PublicIdentityResult.unavailable(cpf);
        }
        final String name = value(
            prioritizedRole,
            "AgentePublicoNome",
            "agentePublicoNome"
        );

        return PublicIdentityResult.found(
            PublicIdentityType.PUBLIC_AGENT,
            cpf,
            sub,
            name,
            name,
            email == null ? null : value(email, "email", "Email"),
            email == null ? null : value(email, "corporativo", "Corporativo"),
            Collections.singletonList(assignment)
        );
    }

    private JSONObject loadEmailIfAvailable(final String sub, final String token) {
        try {
            final GovesHttpResponse response = this.getEmail(sub, token);
            if(response.getStatusCode() != HTTP_OK || isBlank(response.getBody())) {
                return null;
            }
            return new JSONObject(response.getBody());
        }
        catch(final RuntimeException | IOException ignored) {
            return null;
        }
    }

    private GovesHttpResponse getEmail(final String sub, final String token) throws IOException {
        return this.http.exchange(
            "GET",
            this.properties.getAcessoCidadaoBaseUrl(),
            "/api/cidadao/" + sub + "/email",
            token
        );
    }

    private PublicAgentAssignment mapAssignment(final JSONObject role) throws IOException {
        final String organizationToken = this.tokenProvider.getOrganizationToken();
        final String organizationGuid = value(role, "LotacaoGuid", "lotacaoGuid");
        OrganizationInfo organization = null;
        if(!isBlank(organizationGuid)) {
            organization = this.getOrganization(organizationGuid, organizationToken);
            if(organization == null) {
                return null;
            }
        }
        return new PublicAgentAssignment(
            value(role, "Guid", "guid"),
            value(role, "Nome", "nome"),
            value(role, "Tipo", "tipo"),
            organization
        );
    }

    private static JSONObject selectPrioritizedRole(final JSONArray roles) {
        JSONObject firstRole = null;
        for(int i = 0; i < roles.length(); i++) {
            final JSONObject role = roles.optJSONObject(i);
            if(role == null) continue;
            if(firstRole == null) {
                firstRole = role;
            }
            if(role.optBoolean("Prioritario", role.optBoolean("prioritario", false))) {
                return role;
            }
        }
        return firstRole;
    }

    private OrganizationInfo getOrganization(
        final String guid,
        final String token
    ) throws IOException {
        final OrganizationInfo cached = this.organizationCache.get(guid);
        if(cached != null) {
            return cached;
        }
        final GovesHttpResponse response = this.http.exchange(
            "GET",
            this.properties.getOrganizationBaseUrl(),
            "/organizations/" + guid + "/info",
            token
        );
        if(response.getStatusCode() != HTTP_OK) {
            return null;
        }
        final JSONObject json = new JSONObject(response.getBody());
        final OrganizationInfo organization = new OrganizationInfo(
            value(json, "guid", "Guid"),
            value(json, "razaoSocial", "RazaoSocial"),
            value(json, "nomeFantasia", "NomeFantasia"),
            value(json, "sigla", "Sigla"),
            value(json, "guidOrganizacaoPai", "GuidOrganizacaoPai")
        );
        this.organizationCache.put(guid, organization);
        return organization;
    }

    private String findOrganizationGuid(final JSONArray organizations, final String abbreviation) {
        for(int i = 0; i < organizations.length(); i++) {
            final JSONObject organization = organizations.optJSONObject(i);
            if(organization == null) continue;
            if(abbreviation.equalsIgnoreCase(value(organization, "Sigla", "sigla"))) {
                return value(organization, "Guid", "guid");
            }
            final JSONArray children = organization.optJSONArray("Filhos") == null
                ? organization.optJSONArray("filhos")
                : organization.optJSONArray("Filhos");
            if(children != null) {
                final String childGuid = this.findOrganizationGuid(children, abbreviation);
                if(!isBlank(childGuid)) return childGuid;
            }
        }
        return null;
    }

    private static String normalizeText(final String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase();
    }

    private static String value(
        final JSONObject json,
        final String preferredKey,
        final String alternativeKey
    ) {
        final String preferred = json.optString(preferredKey, null);
        return isBlank(preferred) ? json.optString(alternativeKey, null) : preferred;
    }

    private static String firstNotBlank(final String first, final String second) {
        return isBlank(first) ? second : first;
    }

    private static String nameFromEmail(final String email) {
        if(isBlank(email)) return null;
        final int separator = email.indexOf('@');
        return separator > 0 ? email.substring(0, separator) : null;
    }

    private static boolean isBlank(final String value) {
        return value == null || value.trim().isEmpty();
    }
}
