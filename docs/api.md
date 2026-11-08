# API — Plataforma de Feedback de Aulas

Base URL (Flex Consumption):

```text
https://func-feedback-fase4-rsppg4naosvfo-gvdsaba3gje5cnha.southafricanorth-01.azurewebsites.net
```

A URL exata é devolvida pelo deploy (o hostname do Flex não segue
`<app>.azurewebsites.net`):

```powershell
az deployment group show `
  --resource-group rg-feedback-fase4 `
  --name <NOME_DO_DEPLOYMENT> `
  --query 'properties.outputs.functionAppUrl.value' -o tsv
```

## Autenticação

O endpoint exige **function key** (`AuthorizationLevel.FUNCTION`).

Envie de uma das duas formas:

```http
x-functions-key: <FUNCTION_KEY>
```

ou

```text
?code=<FUNCTION_KEY>
```

Sem a chave, a resposta é `401 Unauthorized`.

> A chave é obtida em **Function App → App keys → default**, ou com:
>
> ```powershell
> az functionapp keys list -g rg-feedback-fase4 -n <FUNCTION_APP> --query functionKeys.default -o tsv
> ```

---

## POST /api/avaliacao

Registra uma avaliação de aula.

> O PDF do desafio escreve `POST /avaliação`. A rota implementada é
> `POST /api/avaliacao`, sem acento e com o prefixo padrão do Azure Functions.
> A diferença é intencional e documentada.

### Request

```http
POST /api/avaliacao?code=<FUNCTION_KEY>
Content-Type: application/json

{
  "descricao": "Aula muito confusa, não entendi o conteúdo",
  "nota": 2
}
```

| Campo | Tipo | Obrigatório | Regra |
|---|---|---|---|
| `descricao` | string | sim | Não vazia, até 1.000 caracteres (espaços nas bordas são removidos) |
| `nota` | inteiro | sim | Entre 0 e 10 (inclusive) |

### Response — 201 Created

```json
{
  "id": "7f3c2b1a-9e4d-4c8a-b2f1-0d5e6a7b8c9d",
  "urgencia": "CRITICA",
  "dataEnvio": "2026-11-06T23:10:00Z"
}
```

| Campo | Descrição |
|---|---|
| `id` | UUID gerado no recebimento |
| `urgencia` | `CRITICA`, `MEDIA` ou `BAIXA` |
| `dataEnvio` | Instante UTC em ISO-8601 |

### Regra de urgência

| Nota | Urgência | Dispara e-mail |
|---|---|---|
| 0 a 3 | `CRITICA` | Sim |
| 4 a 6 | `MEDIA` | Não |
| 7 a 10 | `BAIXA` | Não |

### Erros

| Código | Situação | Exemplo de corpo |
|---|---|---|
| `400` | JSON inválido, `descricao` vazia/longa, `nota` fora de 0–10 | `{"erro":"O campo 'nota' deve ser um inteiro entre 0 e 10."}` |
| `401` | Function key ausente ou inválida | — |
| `405` | Método diferente de `POST` | — |
| `500` | Falha inesperada (registrada no Application Insights) | `{"erro":"Erro interno ao processar a avaliação. Tente novamente mais tarde."}` |

### Efeitos assíncronos

1. A avaliação é gravada no Cosmos DB (`data` = `yyyy-MM-dd` em America/Sao_Paulo).
2. Se `urgencia == CRITICA`, uma mensagem é publicada na fila
   `avaliacoes-criticas`.
3. A função `notificacao` consome a fila e envia o e-mail pelo ACS.
4. Se o ACS responder `429`, a mensagem é reenfileirada com o atraso do header
   `Retry-After` (máximo de 5 tentativas).

O `201` confirma o **recebimento e a persistência**, não o envio do e-mail.

---

## Relatório semanal

Não há endpoint HTTP para o relatório: ele é disparado por um **Timer Trigger**
(`0 0 8 * * MON`, segunda-feira às 08:00 em America/Sao_Paulo) e consolidado a
partir do momento do disparo:

- janela: `hoje - 6 dias` até `hoje` (7 dias corridos, não segunda–domingo);
- conteúdo: total, média das notas, quantidade por dia, quantidade por urgência
  e detalhamento (descrição, urgência, data de envio);
- destinatários: `ADMIN_EMAILS`.

Para executar fora do horário (demonstração):

1. Portal → **Function App** → **Functions** → `relatorio` → **Test/Run** → **Run**;
2. ou aguarde o horário configurado em `RELATORIO_CRON`.

---

## Exemplos

### curl (Git Bash / Linux / macOS)

```bash
URL="https://<function-app>.azurewebsites.net/api/avaliacao?code=<FUNCTION_KEY>"

# Não crítica
curl -i -X POST "$URL" \
  -H "Content-Type: application/json" \
  -d '{"descricao":"Aula excelente, muito clara","nota":9}'

# Crítica (dispara e-mail)
curl -i -X POST "$URL" \
  -H "Content-Type: application/json" \
  -d '{"descricao":"Não entendi nada da aula","nota":2}'

# Payload inválido (400)
curl -i -X POST "$URL" \
  -H "Content-Type: application/json" \
  -d '{"descricao":"","nota":11}'
```

### PowerShell

```powershell
$url = "https://<function-app>.azurewebsites.net/api/avaliacao?code=<FUNCTION_KEY>"

Invoke-RestMethod -Method Post -Uri $url -ContentType "application/json" `
  -Body (@{ descricao = "Aula excelente, muito clara"; nota = 9 } | ConvertTo-Json)

Invoke-RestMethod -Method Post -Uri $url -ContentType "application/json" `
  -Body (@{ descricao = "Não entendi nada da aula"; nota = 2 } | ConvertTo-Json)
```

### Script pronto

```powershell
.\scripts\smoke-test.ps1 -ResourceGroup rg-feedback-fase4
```

O script testa os cenários `201` (não crítica), `201` (crítica), `400` e `401`.
