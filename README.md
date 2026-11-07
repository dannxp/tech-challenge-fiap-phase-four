# Tech Challenge FIAP — Fase 4

Plataforma **serverless** de feedback de aulas, construída em **Java 21 + Spring
Boot** e hospedada no **Microsoft Azure**.

Recebe avaliações (nota de 0 a 10), calcula a urgência, notifica administradores
por e-mail quando a avaliação é crítica e envia um relatório semanal consolidado
com média das notas, contagem por dia e contagem por urgência.

> Repositório público: <https://github.com/dannxp/tech-challenge-fiap-phase-four>

## Arquitetura

```text
POST /api/avaliacao
        │
        ▼
  ┌─────────────┐      grava       ┌──────────────┐
  │  avaliacao  │ ───────────────► │  Cosmos DB   │
  │  (HTTP)     │                  └──────────────┘
  └──────┬──────┘
         │ nota 0–3 (CRITICA)
         ▼
  ┌──────────────────┐   e-mail   ┌─────────────────────┐
  │  Storage Queue   │ ─────────► │  notificacao        │
  │ avaliacoes-...   │            │  (Queue Trigger)    │
  └──────────────────┘            │  ACS Email          │
                                  └─────────────────────┘

  Timer seg 08:00 (America/Sao_Paulo)
        │
        ▼
  ┌─────────────┐   últimos 7 dias   ┌──────────────────┐
  │  relatorio  │ ─────────────────► │  Cosmos DB       │
  │  (Timer)    │ ◄───────────────── └──────────────────┘
  └──────┬──────┘
         │ e-mail HTML + texto
         ▼
   Administradores (ACS Email)
```

### As 3 funções (SRP)

| Função | Gatilho | Responsabilidade |
|---|---|---|
| `avaliacao` | HTTP `POST /api/avaliacao` | validar, calcular urgência, persistir e enfileirar críticas |
| `notificacao` | Queue Trigger | enviar e-mail de crítica e tratar `429` do ACS |
| `relatorio` | Timer Trigger | consolidar 7 dias e enviar o relatório semanal |

### Regra de urgência

| Nota | Urgência | Notifica |
|---|---|---|
| 0–3 | `CRITICA` | sim |
| 4–6 | `MEDIA` | não |
| 7–10 | `BAIXA` | não |

## Stack

- **Java 21** + **Spring Boot 3.5** + **Spring Cloud Function** (adapter Azure)
- **Azure Functions** — Flex Consumption, Linux
- **Azure Cosmos DB** — free tier, partition key `/data`, TTL 365 dias
- **Azure Storage Queue** — desacoplamento da notificação
- **Azure Communication Services — Email**
- **Key Vault + Managed Identity + Entra ID RBAC**
- **Application Insights + Log Analytics**
- **Bicep** + **GitHub Actions (OIDC)**

## Ambiente publicado

| Item | Valor |
|---|---|
| Resource Group | `rg-feedback-fase4` |
| Região | `southafricanorth` |
| Plano | Flex Consumption (FC1, Linux, Java 21, 2048 MB) |
| Function App | `func-feedback-fase4-rsppg4naosvfo` |
| Cosmos DB | `cosmos-f4-rsppg4naosvfo` (`feedback/avaliacoes`) |
| Storage | `stfeedbackfase4rsppg4nao` |
| Key Vault | `kv-f4-rsppg4naosvfo` |
| Application Insights | `appi-feedback-fase4` |

## Como executar

### Deploy completo (PowerShell)

```powershell
az login

.\scripts\deploy.ps1 `
  -ResourceGroup "rg-feedback-fase4" `
  -AdminEmails "admin@exemplo.com" `
  -AcsSender "DoNotReply@<guid>.azurecomm.net"
```

O script compila, provisiona a infraestrutura, grava a connection string do ACS
no Key Vault e publica o código.

### Smoke test

```powershell
.\scripts\smoke-test.ps1 -ResourceGroup "rg-feedback-fase4"
```

### Build local

```powershell
mvn -B clean verify
```

O build gera o pacote em `target/azure-functions/func-feedback-fase4`.

## Endpoint

Endpoint publicado (Function key obrigatória):

```text
https://func-feedback-fase4-rsppg4naosvfo-gvdsaba3gje5cnha.southafricanorth-01.azurewebsites.net/api/avaliacao
```

```http
POST /api/avaliacao?code=<FUNCTION_KEY>
Content-Type: application/json

{
  "descricao": "Aula muito confusa, não entendi o conteúdo",
  "nota": 2
}
```

```json
{
  "id": "7f3c2b1a-9e4d-4c8a-b2f1-0d5e6a7b8c9d",
  "urgencia": "CRITICA",
  "dataEnvio": "2026-11-06T23:10:00Z"
}
```

Contrato completo em [docs/api.md](docs/api.md).

> O PDF escreve `POST /avaliação`. A rota implementada é `POST /api/avaliacao`,
> sem acento e com o prefixo padrão do Functions. A diferença é intencional.

## Estrutura

```text
├─ infra/               # Bicep (main + módulos)
├─ scripts/             # deploy.ps1 e smoke-test.ps1
├─ src/main/java/       # domínio, aplicação, infraestrutura e funções
├─ src/main/resources/  # application.yml + templates HTML
├─ src/test/java/       # testes unitários (JUnit 5 + Mockito + AssertJ)
├─ docs/                # arquitetura, setup do Azure e API
└─ .github/workflows/   # CI e Deploy (OIDC)
```

## Segurança

- Function key no endpoint de entrada.
- Cosmos DB com **chave local desabilitada** e acesso via Managed Identity.
- ACS connection string apenas no **Key Vault**, referenciada por
  `@Microsoft.KeyVault(...)`.
- Nenhum segredo, subscription ID ou e-mail de admin versionado.
- `local.settings.json` e `target/` no `.gitignore`.

## Documentação

| Documento | Conteúdo |
|---|---|
| [docs/arquitetura.md](docs/arquitetura.md) | Decisões, fluxos, modelo de dados, segurança, custos e roteiro do vídeo |
| [docs/setup-azure.md](docs/setup-azure.md) | Runbook de deploy, OIDC, GitHub Actions e troubleshooting |
| [docs/api.md](docs/api.md) | Contrato da API, erros e exemplos |

## Status

- [x] Análise do enunciado e arquitetura
- [x] Implementação das 3 funções e testes unitários
- [x] Infraestrutura Bicep
- [x] Pipeline CI/CD com OIDC
- [x] Deploy final na assinatura Azure
- [x] E-mail crítico e relatório semanal validados
- [ ] Vídeo de demonstração

## Vídeo

> Link do vídeo de demonstração: _a preencher_
