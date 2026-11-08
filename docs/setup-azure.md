# Configuração da Azure

Este documento resume o que é necessário para publicar o projeto. O código de
infraestrutura fica em `infra/`, e os scripts de publicação ficam em `scripts/`.

## Pré-requisitos

- JDK 21;
- Maven 3.9 ou superior;
- Azure CLI;
- PowerShell;
- uma assinatura Azure com permissão para criar recursos e atribuições RBAC.

Neste projeto, todos os recursos foram publicados em
`southafricanorth`. A região foi escolhida porque era permitida pela política
da assinatura utilizada e aceitava o Azure Functions Flex Consumption.

Faça login e selecione a assinatura antes de começar:

```powershell
az login
az account set --subscription "<SUBSCRIPTION_ID>"
az account show --query "{subscriptionId:id, tenantId:tenantId, name:name}" -o table
```

## Recursos necessários

O Bicep cria ou atualiza:

- Storage Account e a fila `avaliacoes-criticas`;
- Cosmos DB com o banco `feedback` e o container `avaliacoes`;
- Key Vault;
- Application Insights e Log Analytics;
- plano Flex Consumption e Function App;
- identidades e permissões RBAC necessárias.

O Azure Communication Services Email e o domínio remetente são configurados
fora do Bicep. É necessário ter um remetente verificado, por exemplo
`DoNotReply@<guid>.azurecomm.net`.

## Deploy local

Na raiz do projeto, execute:

```powershell
.\scripts\deploy.ps1 `
  -ResourceGroup "rg-feedback-fase4" `
  -AdminEmails "admin@exemplo.com" `
  -AcsSender "DoNotReply@<guid>.azurecomm.net"
```

O script:

1. compila o projeto;
2. provisiona a infraestrutura com o Bicep;
3. grava a connection string do ACS no Key Vault;
4. publica o pacote no Function App;
5. informa a URL e a chave da função.

A connection string pode ser informada quando o script solicitar ou pela
variável de ambiente `ACS_CONNECTION_STRING`. Ela não deve ser colocada no
repositório.

Para reaproveitar um pacote já compilado:

```powershell
.\scripts\deploy.ps1 `
  -AdminEmails "admin@exemplo.com" `
  -AcsSender "DoNotReply@<guid>.azurecomm.net" `
  -SkipPackage
```

## Validação

Depois do deploy, execute:

```powershell
.\scripts\smoke-test.ps1 -ResourceGroup "rg-feedback-fase4"
```

O script verifica:

| Cenário | Resultado esperado |
|---|---|
| Nota 9 | `201` |
| Nota 2 | `201` e notificação crítica |
| Payload inválido | `400` |
| Sem Function Key | `401` ou `403` |

O relatório semanal pode ser executado manualmente no portal:

**Function App → Functions → `relatorio` → Test/Run → Run**

## GitHub Actions

O workflow de deploy usa autenticação OIDC. Para configurá-lo, crie uma App
Registration no Entra ID e uma credencial federada para o repositório e a
branch `main`.

O subject da credencial deve seguir este formato:

```text
repo:dannxp/tech-challenge-fiap-phase-four:ref:refs/heads/main
```

Conceda à identidade do workflow as permissões `Contributor` e
`Role Based Access Control Administrator` no Resource Group. A segunda
permissão é necessária porque o Bicep cria atribuições de função.

No repositório, configure as seguintes variables:

| Variable | Conteúdo |
|---|---|
| `AZURE_CLIENT_ID` | ID da aplicação criada no Entra ID |
| `AZURE_TENANT_ID` | ID do tenant |
| `AZURE_SUBSCRIPTION_ID` | ID da assinatura |
| `AZURE_RESOURCE_GROUP` | `rg-feedback-fase4` |
| `AZURE_LOCATION` | `southafricanorth` |
| `ACS_SENDER` | Remetente verificado do ACS |

Configure `ADMIN_EMAILS` como secret. A connection string do ACS continua
somente no Key Vault; ela não é necessária no GitHub Actions.

Depois disso, um push na `main` inicia o workflow de CI e deploy.

## Problemas comuns

| Problema | O que conferir |
|---|---|
| `401` com uma chave válida | Verifique se a chave pertence ao Function App correto. |
| E-mail não chegou | Confira spam e o limite do domínio gerenciado do ACS. |
| `429` no ACS | Aguarde o limite do serviço; a aplicação reprocessa a mensagem. |
| `0 functions found` | Publique usando `scripts/deploy.ps1` ou o workflow. |
| OIDC falhou | Confira o subject da credencial, o tenant e a branch. |
| Relatório não foi enviado | Verifique `ADMIN_EMAILS` e execute a função manualmente. |

Não versionar `local.settings.json`, chaves, tokens ou connection strings.
