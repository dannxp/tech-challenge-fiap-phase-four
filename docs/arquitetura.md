# Arquitetura

## Contexto

Este projeto recebe avaliações de aulas e classifica cada uma de acordo com a
nota recebida. Avaliações críticas geram uma notificação por e-mail. Uma rotina
semanal reúne os feedbacks recentes e envia um resumo aos administradores.

A solução foi implementada como uma aplicação serverless na Azure. Não existe
um servidor dedicado: cada parte da aplicação é executada quando o seu gatilho
é acionado.

## Visão geral

```text
Cliente
  │
  ▼
avaliacao (HTTP) ───────────────► Cosmos DB
  │
  └── nota de 0 a 3 ───────────► Storage Queue
                                      │
                                      ▼
                                notificacao ───► ACS Email

Timer semanal ─────────────────► relatorio ────► Cosmos DB
                                                     │
                                                     └──► ACS Email
```

## Funções

| Função | Gatilho | Responsabilidade |
|---|---|---|
| `avaliacao` | HTTP POST | Validar, classificar e salvar o feedback. Também coloca avaliações críticas na fila. |
| `notificacao` | Storage Queue | Ler a fila e enviar o aviso de avaliação crítica. |
| `relatorio` | Timer semanal | Consultar os últimos sete dias e enviar o resumo. |

A divisão mantém uma responsabilidade clara para cada função. O recebimento de
uma avaliação não fica esperando o envio do e-mail terminar: a avaliação é
salva e a notificação segue de forma assíncrona pela fila.

## Regras de negócio

| Nota | Urgência | Notificação |
|---|---|---|
| 0 a 3 | `CRITICA` | Sim |
| 4 a 6 | `MEDIA` | Não |
| 7 a 10 | `BAIXA` | Não |

O relatório é executado às 08:00 de segunda-feira, no fuso
`America/Sao_Paulo`. A consulta considera sete dias corridos a partir do
momento da execução.

## Serviços utilizados

| Serviço | Uso no projeto |
|---|---|
| Azure Functions | Execução das três funções no plano Flex Consumption. |
| Cosmos DB | Persistência das avaliações. |
| Storage Queue | Desacoplamento entre o recebimento e a notificação. |
| Azure Communication Services Email | Envio dos e-mails. |
| Key Vault | Armazenamento da connection string do ACS. |
| Managed Identity | Acesso da aplicação aos recursos Azure sem chaves no código. |
| Application Insights | Logs e acompanhamento da execução. |
| Bicep | Provisionamento da infraestrutura. |
| GitHub Actions | Build, testes e deploy automático. |

O ACS Email e o domínio remetente foram configurados separadamente na Azure.
O Bicep cria a infraestrutura usada pela aplicação e o deploy grava a
connection string no Key Vault.

## Organização do código

O código segue uma separação próxima da arquitetura hexagonal:

```text
src/main/java/br/com/fiap/feedback/
├── domain/          entidades e regras de negócio
├── application/     casos de uso, DTOs e portas
├── functions/       adaptadores de entrada da Azure Functions
├── infrastructure/  Cosmos DB, fila, e-mail e telemetria
└── config/          configuração da aplicação
```

O domínio não conhece os serviços da Azure. Os casos de uso dependem de
interfaces, e as classes em `infrastructure` implementam essas interfaces.
Isso deixa as regras de negócio testáveis sem uma conexão real com a nuvem.

## Segurança

- O endpoint HTTP exige Function Key.
- O Cosmos DB está configurado sem autenticação por chave local.
- A aplicação usa Managed Identity para acessar Cosmos DB, Storage Queue e Key
  Vault.
- A connection string do ACS fica no Key Vault e não é versionada.
- O deploy do GitHub Actions usa OIDC, sem publish profile armazenado no
  repositório.
- A descrição do feedback é escapada antes de ser usada no HTML do e-mail.

## Observabilidade e tratamento de falhas

A aplicação registra eventos de recebimento, envio, reprocessamento e geração
do relatório no Application Insights. Quando o ACS retorna `429`, a mensagem é
reenfileirada respeitando o tempo indicado pelo serviço, com limite de
tentativas.

O projeto não mantém um alerta HTTP 5xx no Bicep porque essa métrica não está
disponível da mesma forma no plano Flex Consumption utilizado. O
acompanhamento fica pelos logs e métricas do Application Insights.

## Infraestrutura e deploy

Os arquivos em `infra/` definem os recursos Azure. Os scripts em `scripts/`
fazem o deploy local e executam o smoke test. O workflow em
`.github/workflows/deploy.yml` repete o processo no GitHub Actions.

O pipeline executa:

1. compilação e testes;
2. provisionamento ou atualização da infraestrutura;
3. publicação do pacote no Function App;
4. validação do deploy.

O ambiente usado na entrega está na região `southafricanorth`, em razão das
restrições da assinatura Azure utilizada.

## Testes

Os testes unitários cobrem as regras de urgência, os casos de uso de
registro/notificação/relatório, o mapeamento do Cosmos e os templates de
e-mail. O script `scripts/smoke-test.ps1` valida o endpoint publicado nos
cenários de sucesso, payload inválido e ausência de chave.

## Requisitos atendidos

- processamento serverless em cloud;
- pelo menos duas funções com responsabilidades separadas;
- persistência das avaliações;
- notificação de avaliações críticas;
- relatório semanal;
- infraestrutura como código;
- segurança dos segredos;
- monitoramento;
- deploy automatizado.
