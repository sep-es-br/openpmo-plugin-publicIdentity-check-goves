package br.gov.es.pmo.user_a_identify.goves;

import br.gov.es.pmo.user_a_identify.goves.client.GovesClientTokenProvider;
import br.gov.es.pmo.user_a_identify.goves.client.GovesHttpGateway;
import br.gov.es.pmo.user_a_identify.goves.client.GovesHttpResponse;
import br.gov.es.pmo.user_a_identify.goves.configuration.GovesPublicIdentityProperties;
import br.gov.es.pmo.user_a_identify.model.IPublicIdentityProvider;
import br.gov.es.pmo.user_a_identify.model.PublicAgentAssignment;
import br.gov.es.pmo.user_a_identify.model.PublicAgentInfo;
import br.gov.es.pmo.user_a_identify.model.PublicAgentInfoResult;
import br.gov.es.pmo.user_a_identify.model.PublicAgentSearchResult;
import br.gov.es.pmo.user_a_identify.model.PublicAgentSummary;
import br.gov.es.pmo.user_a_identify.model.PublicIdentityResult;
import br.gov.es.pmo.user_a_identify.model.PublicIdentityType;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class GovesPublicIdentityProvider implements IPublicIdentityProvider {

    private static final int HTTP_OK = 200;
    private static final int HTTP_NOT_FOUND = 404;

    private final GovesHttpGateway http;
    private final GovesClientTokenProvider tokenProvider;
    private final GovesPublicIdentityProperties properties;

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

    @Override
    public PublicAgentInfoResult findPublicAgentInformationBySub(final String sub) {
        if(isBlank(sub)) {
            return PublicAgentInfoResult.notFound();
        }

        try {
            final String token = this.tokenProvider.getAcessoCidadaoToken();
            final GovesHttpResponse agentInformationResponse = this.getAgentInformation(sub, token);

            if(agentInformationResponse.getStatusCode() == HTTP_NOT_FOUND) {
                return PublicAgentInfoResult.notFound();
            }
            if(agentInformationResponse.getStatusCode() != HTTP_OK) {
                return PublicAgentInfoResult.unavailable();
            }

            final JSONObject json = new JSONObject(agentInformationResponse.getBody());
            final PublicAgentInfo agentPublicInfo = new PublicAgentInfo(
                value(json, "Sub", "sub"),
                longValue(json, "SubDescontinuado", "subDescontinuado"),
                value(json, "Nome", "nome"),
                value(json, "Apelido", "apelido"),
                value(json, "Email", "email")
            );
            return PublicAgentInfoResult.found(agentPublicInfo);
        }
        catch(final RuntimeException | IOException e) {
            return PublicAgentInfoResult.unavailable();
        }
    }

    private GovesHttpResponse getAgentInformation(final String sub, final String token) throws IOException {
        return this.http.exchange(
            "GET",
            this.properties.getAcessoCidadaoBaseUrl(),
            "/api/agentepublico/" + sub,
            token
        );
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

    private PublicAgentAssignment mapAssignment(final JSONObject role) {
        final String workLocationGuid = value(role, "LotacaoGuid", "lotacaoGuid");
        return new PublicAgentAssignment(
            value(role, "Guid", "guid"),
            value(role, "Nome", "nome"),
            value(role, "Tipo", "tipo"),
            workLocationGuid,
            null
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

    private static Long longValue(
        final JSONObject json,
        final String preferredKey,
        final String alternativeKey
    ) {
        final Object rawValue = json.has(preferredKey)
            ? json.opt(preferredKey)
            : json.opt(alternativeKey);
        if(rawValue == null || JSONObject.NULL.equals(rawValue)) {
            return null;
        }
        if(rawValue instanceof Number) {
            return ((Number) rawValue).longValue();
        }
        final String text = String.valueOf(rawValue);
        return isBlank(text) ? null : Long.valueOf(text);
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
