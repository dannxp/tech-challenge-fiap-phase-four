targetScope = 'resourceGroup'

// ============================================================================
// Plataforma serverless de feedback de aulas — FIAP Fase 4
//
// Recursos criados:
//  - Storage Account (runtime do Functions + fila avaliacoes-criticas)
//  - Cosmos DB for NoSQL (free tier, database feedback, container avaliacoes)
//  - Key Vault (segredo acs-connection-string gravado pelo script de deploy)
//  - Log Analytics + Application Insights
//  - Function App Linux Flex Consumption (Java 21) + Managed Identity
//  - Role assignments (Cosmos, Storage Queue, Key Vault)
//  - Action Group para notificações operacionais
//
// Nenhum segredo é versionado. A connection string do ACS entra no Key Vault
// via scripts/deploy.ps1 e é referenciada com @Microsoft.KeyVault(...).
// ============================================================================

@description('Região do Storage Account e do Function App. Precisa estar na lista permitida pela política.')
param location string = 'southafricanorth'

@description('Região do Key Vault.')
param keyVaultLocation string = 'southafricanorth'

@description('Região do Log Analytics / Application Insights.')
param monitoringLocation string = 'southafricanorth'

@description('Região da conta Cosmos DB. Pode diferir do restante por disponibilidade regional.')
param cosmosLocation string = 'southafricanorth'

@description('Sufixo usado nos nomes globais. Padrão determinístico por Resource Group.')
param nameSuffix string = toLower(uniqueString(resourceGroup().id))

@description('E-mails dos administradores separados por vírgula. Informado no deploy, não versionado.')
param adminEmails string

@description('Remetente do Azure Communication Services (ex.: DoNotReply@<guid>.azurecomm.net).')
param acsSender string

@description('Cron NCRONTAB (6 campos) do relatório semanal.')
param relatorioCron string = '0 0 8 * * MON'

@description('Fuso horário da aplicação e do host do Functions.')
param timezone string = 'America/Sao_Paulo'

@description('Habilita o free tier do Cosmos DB (uma conta por assinatura).')
param enableCosmosFreeTier bool = true

@description('Tags aplicadas aos recursos.')
param tags object = {
  project: 'tech-challenge-fiap-fase4'
  environment: 'demo'
}

@description('Nome do plano Flex Consumption. Em atualizações, use o plano já associado ao Function App.')
param planName string = 'asp-feedback-fase4'

@description('Cria as atribuições RBAC. Desabilite ao reutilizar uma identidade já provisionada.')
param deployRoles bool = true

var storageAccountName = toLower(take('stfeedbackfase4${nameSuffix}', 24))
var cosmosAccountName = take('cosmos-f4-${nameSuffix}', 44)
var keyVaultName = take('kv-f4-${nameSuffix}', 24)
var functionAppName = take('func-feedback-fase4-${nameSuffix}', 60)
var logAnalyticsName = 'log-feedback-fase4'
var appInsightsName = 'appi-feedback-fase4'
var actionGroupName = 'ag-feedback-fase4'
var databaseName = 'feedback'
var containerName = 'avaliacoes'
var queueName = 'avaliacoes-criticas'
var deploymentContainerName = 'app-package'
var acsSecretName = 'acs-connection-string'

module storageModule './modules/storage.bicep' = {
  name: 'storage'
  params: {
    name: storageAccountName
    location: location
    queueName: queueName
    tags: tags
  }
}

module cosmosModule './modules/cosmos.bicep' = {
  name: 'cosmos'
  params: {
    name: cosmosAccountName
    location: cosmosLocation
    databaseName: databaseName
    containerName: containerName
    enableFreeTier: enableCosmosFreeTier
    tags: tags
  }
}

module keyVaultModule './modules/key-vault.bicep' = {
  name: 'key-vault'
  params: {
    name: keyVaultName
    location: keyVaultLocation
    tags: tags
  }
}

module monitoringModule './modules/monitoring.bicep' = {
  name: 'monitoring'
  params: {
    logAnalyticsName: logAnalyticsName
    appInsightsName: appInsightsName
    location: monitoringLocation
    tags: tags
  }
}

module functionAppModule './modules/function-app.bicep' = {
  name: 'function-app'
  params: {
    name: functionAppName
    location: location
    planName: planName
    storageAccountName: storageModule.outputs.name
    deploymentContainerName: deploymentContainerName
    tags: tags
    appSettings: {
      MAIN_CLASS: 'br.com.fiap.feedback.FeedbackApplication'
      TZ: timezone
      WEBSITE_TIME_ZONE: timezone
      COSMOS_ENDPOINT: cosmosModule.outputs.endpoint
      COSMOS_DATABASE: databaseName
      COSMOS_CONTAINER: containerName
      STORAGE_QUEUE_ENDPOINT: storageModule.outputs.queueEndpoint
      NOTIFICACOES_QUEUE: queueName
      ACS_CONNECTION_STRING: '@Microsoft.KeyVault(VaultName=${keyVaultModule.outputs.name};SecretName=${acsSecretName})'
      ACS_SENDER: acsSender
      ADMIN_EMAILS: adminEmails
      RELATORIO_CRON: relatorioCron
      APPLICATIONINSIGHTS_CONNECTION_STRING: monitoringModule.outputs.appInsightsConnectionString
      JAVA_OPTS: '-XX:+UseSerialGC -XX:MaxRAMPercentage=75'
    }
  }
}

module rolesModule './modules/roles.bicep' = if (deployRoles) {
  name: 'roles'
  params: {
    functionPrincipalId: functionAppModule.outputs.principalId
    cosmosAccountName: cosmosModule.outputs.name
    storageAccountName: storageModule.outputs.name
    keyVaultName: keyVaultModule.outputs.name
  }
}

module alertsModule './modules/alerts.bicep' = {
  name: 'alerts'
  params: {
    actionGroupName: actionGroupName
    adminEmails: adminEmails
    tags: tags
  }
}

@description('Nome do Function App.')
output functionAppName string = functionAppModule.outputs.name

@description('Hostname público do Function App.')
output functionAppHostName string = functionAppModule.outputs.defaultHostName

@description('URL base do Function App.')
output functionAppUrl string = 'https://${functionAppModule.outputs.defaultHostName}'

@description('Nome do Key Vault.')
output keyVaultName string = keyVaultModule.outputs.name

@description('Nome do segredo que guarda a connection string do ACS.')
output acsSecretName string = acsSecretName

@description('Nome do Storage Account.')
output storageAccountName string = storageModule.outputs.name

@description('Nome da conta Cosmos DB.')
output cosmosAccountName string = cosmosModule.outputs.name

@description('Nome do Application Insights.')
output appInsightsName string = appInsightsName

@description('Nome do Resource Group.')
output resourceGroupName string = resourceGroup().name
