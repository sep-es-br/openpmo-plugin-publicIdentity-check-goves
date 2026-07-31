# OpenPMO Public Identity Check GOVES

Implementação GOVES do contrato de validação de identidade pública do OpenPMO.

## Fluxos implementados

### Consulta por CPF

1. Confirma a existência com `GET /api/cidadao/{cpf}`.
2. Somente após resposta `200`, obtém o `sub` com `PUT /api/cidadao/{cpf}/pesquisaSub`.
3. Consulta `GET /api/agentepublico/{sub}` para distinguir cidadão comum de agente público.

O `PUT pesquisaSub` não é chamado quando a consulta inicial retorna `404`.

### Agentes públicos

- Pesquisa agentes por nome.
- Carrega agente por `sub`.
- Carrega e-mail, papéis e lotações.
- Consulta os dados dos órgãos por GUID.
- Evita consultas repetidas do mesmo órgão por meio de cache.

## Autenticação

As integrações usam OAuth2 Client Credentials. Credenciais não são armazenadas neste projeto; elas devem ser fornecidas pela aplicação consumidora.

## Compatibilidade

- Java 8
- Spring Boot 2.2
- Carregamento automático por `META-INF/spring.factories`

## Dependência do contrato

Antes do build local, publique o projeto da interface:

```powershell
cd ..\openpmo-plugin-publicIdentity_check-interface
.\gradlew.bat publishToMavenLocal
```

## Build e testes

```powershell
.\gradlew.bat clean test
```

## Publicação local

```powershell
.\gradlew.bat publishToMavenLocal
```

Artefato:

```text
com.github.sep-es-br:openpmo-plugin-publicIdentity-check-goves:v1.0.2
```

## Licença

Este projeto é distribuído sob a licença GNU General Public License v3.0. Consulte o arquivo [LICENSE](LICENSE).
