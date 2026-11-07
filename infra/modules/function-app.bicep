// Function App Linux no Flex Consumption (FC1) com Java 21.
// A identidade gerenciada é usada para Cosmos DB, Storage Queue e Key Vault.
// O pacote de código é publicado no container de deployment do Storage Account.

@description('Nome do Function App.')
param name string

@description('Região do Function App.')
param location string

@description('Nome do plano Flex Consumption.')
param planName string

@description('Nome do Storage Account usado pelo host e pelo pacote do Functions.')
param storageAccountName string

@description('Nome do container de blobs onde o pacote de deployment é publicado.')
param deploymentContainerName string = 'app-package'

@description('Memória (MB) de cada instância.')
param instanceMemoryMB int = 2048

@description('Número máximo de instâncias do plano.')
param maximumInstanceCount int = 100

@description('App settings da aplicação. Nenhum segredo é embutido no template.')
param appSettings object

@description('Tags aplicadas aos recursos.')
param tags object = {}

// A connection string é resolvida aqui dentro (e não recebida como parâmetro)
// para que a validação de preflight do Microsoft.Web consiga acessar a conta
// de storage já criada no mesmo deployment.
resource storage 'Microsoft.Storage/storageAccounts@2023-05-01' existing = {
  name: storageAccountName
}

var storageConnectionString = 'DefaultEndpointsProtocol=https;AccountName=${storage.name};AccountKey=${storage.listKeys().keys[0].value};EndpointSuffix=${environment().suffixes.storage}'

var extraAppSettings = [
  for setting in items(appSettings): {
    name: setting.key
    value: string(setting.value)
  }
]

resource plan 'Microsoft.Web/serverfarms@2023-12-01' = {
  name: planName
  location: location
  kind: 'functionapp'
  tags: tags
  sku: {
    name: 'FC1'
    tier: 'FlexConsumption'
  }
  properties: {
    reserved: true
  }
}

resource functionApp 'Microsoft.Web/sites@2023-12-01' = {
  name: name
  location: location
  kind: 'functionapp,linux'
  tags: tags
  identity: {
    type: 'SystemAssigned'
  }
  properties: {
    serverFarmId: plan.id
    httpsOnly: true
    clientAffinityEnabled: false
    publicNetworkAccess: 'Enabled'
    functionAppConfig: {
      deployment: {
        storage: {
          type: 'blobContainer'
          value: 'https://${storage.name}.blob.${environment().suffixes.storage}/${deploymentContainerName}'
          authentication: {
            type: 'StorageAccountConnectionString'
            storageAccountConnectionStringName: 'DEPLOYMENT_STORAGE_CONNECTION_STRING'
          }
        }
      }
      runtime: {
        name: 'java'
        version: '21'
      }
      scaleAndConcurrency: {
        maximumInstanceCount: maximumInstanceCount
        instanceMemoryMB: instanceMemoryMB
      }
    }
    siteConfig: {
      appSettings: concat(
        [
          {
            name: 'AzureWebJobsStorage'
            value: storageConnectionString
          }
          {
            name: 'DEPLOYMENT_STORAGE_CONNECTION_STRING'
            value: storageConnectionString
          }
        ],
        extraAppSettings
      )
    }
  }
}

@description('Resource ID do Function App.')
output id string = functionApp.id

@description('Nome do Function App.')
output name string = functionApp.name

@description('Hostname padrão (sem https://).')
output defaultHostName string = functionApp.properties.defaultHostName

@description('Principal ID da identidade gerenciada atribuída pelo sistema.')
output principalId string = functionApp.identity.principalId

@description('Nome do container de deployment.')
output deploymentContainerName string = deploymentContainerName
