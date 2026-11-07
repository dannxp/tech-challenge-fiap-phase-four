# Feedback de aulas — Tech Challenge FIAP

Projeto desenvolvido para o Tech Challenge da FIAP — Fase 4.

A aplicação recebe avaliações de aulas, classifica a urgência de cada feedback e
envia uma notificação por e-mail quando a nota é crítica. Uma função agendada
também consolida os feedbacks da última semana e envia um relatório aos
administradores.

## Como funciona

O fluxo principal é dividido em três funções:

- **Avaliacao**: recebe o feedback por HTTP, valida os dados, calcula a
  urgência, salva a avaliação e envia as avaliações críticas para uma fila.
- **Notificacao**: consome a fila e envia o e-mail de notificação.
- **Relatorio**: executa semanalmente, consulta as avaliações recentes e envia o
  relatório consolidado.

As avaliações são classificadas conforme a nota:

| Nota | Urgência | Notificação |
|---|---|---|
| 0 a 3 | Crítica | Sim |
| 4 a 6 | Média | Não |
| 7 a 10 | Baixa | Não |

O fluxo entre os componentes é:

```text
HTTP
  │
  ▼
Avaliacao ──► Cosmos DB
  │
  └── avaliação crítica ──► Storage Queue ──► Notificacao ──► ACS Email

Timer semanal ──► Relatorio ──► Cosmos DB ──► ACS Email
```

## Arquitetura

O projeto foi organizado de forma que as regras de negócio não dependam
diretamente dos serviços da Azure.

- `domain`: entidades e regras de negócio;
- `application`: casos de uso e portas;
- `functions`: entradas HTTP, fila e timer;
- `infrastructure`: integrações com Cosmos DB, Storage Queue, ACS Email e
  telemetria;
- `infra`: definição da infraestrutura em Bicep.

Essa separação permite testar os casos de uso sem precisar executar os serviços
da nuvem.

## Tecnologias

- Java 21;
- Spring Boot e Spring Cloud Function;
- Azure Functions, no modelo Flex Consumption;
- Azure Cosmos DB;
- Azure Storage Queue;
- Azure Communication Services Email;
- Azure Key Vault e Managed Identity;
- Application Insights;
- Bicep;
- GitHub Actions com autenticação OIDC.

## Executar os testes

Com Java 21 e Maven instalados:

```bash
mvn -B clean verify
```

O comando compila o projeto, executa os testes e gera o pacote para o Azure
Functions em `target/azure-functions/`.

O deploy e a configuração do ambiente Azure estão descritos em
[docs/setup-azure.md](docs/setup-azure.md).

## API

A aplicação expõe o endpoint:

```text
POST /api/avaliacao
```

Exemplo de requisição:

```json
{
  "descricao": "A aula foi difícil de acompanhar",
  "nota": 2
}
```

O endpoint utiliza Function Key quando executado na Azure. O contrato completo,
incluindo respostas e validações, está em [docs/api.md](docs/api.md).

## Documentação

- [Arquitetura](docs/arquitetura.md): decisões, componentes e fluxos da solução;
- [API](docs/api.md): contrato do endpoint e exemplos;
- [Configuração da Azure](docs/setup-azure.md): configuração do ambiente e
  deploy.

## Estrutura principal

```text
src/
├── main/java/
│   └── br/com/fiap/feedback/
│       ├── application/
│       ├── config/
│       ├── domain/
│       ├── functions/
│       └── infrastructure/
└── test/java/

infra/
scripts/
docs/
.github/workflows/
```
