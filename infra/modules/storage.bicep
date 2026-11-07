// Storage Account: storage do runtime do Functions, fila de notificações e
// eventual poison queue. A connection string NÃO está no repositório: é lida
// pelo Bicep em tempo de deploy (listKeys) e usada apenas como app setting do
// host. A aplicação usa Managed Identity para publicar na fila.

@description('Nome globalmente único do Storage Account (3-24 caracteres, minúsculo).')
param name string

@description('Região do recurso.')
param location string

@description('Nome da fila de notificações críticas.')
param queueName string = 'avaliacoes-criticas'

@description('Nome do container de blobs usado para o pacote de deployment do Functions.')
param deploymentContainerName string = 'app-package'

@description('Tags aplicadas ao recurso.')
param tags object = {}

resource storage 'Microsoft.Storage/storageAccounts@2023-05-01' = {
  name: name
  location: location
  kind: 'StorageV2'
  sku: {
    name: 'Standard_LRS'
  }
  tags: tags
  properties: {
    allowBlobPublicAccess: false
    allowSharedKeyAccess: true
    minimumTlsVersion: 'TLS1_2'
    supportsHttpsTrafficOnly: true
    networkAcls: {
      defaultAction: 'Allow'
      bypass: 'AzureServices'
    }
  }
}

resource queueService 'Microsoft.Storage/storageAccounts/queueServices@2023-05-01' = {
  parent: storage
  name: 'default'
}

resource queue 'Microsoft.Storage/storageAccounts/queueServices/queues@2023-05-01' = {
  parent: queueService
  name: queueName
}

resource blobService 'Microsoft.Storage/storageAccounts/blobServices@2023-05-01' = {
  parent: storage
  name: 'default'
}

resource deploymentContainer 'Microsoft.Storage/storageAccounts/blobServices/containers@2023-05-01' = {
  parent: blobService
  name: deploymentContainerName
  properties: {
    publicAccess: 'None'
  }
}

@description('Resource ID do Storage Account.')
output id string = storage.id

@description('Nome do Storage Account.')
output name string = storage.name

@description('Endpoint de fila usado pelo cliente com Managed Identity.')
output queueEndpoint string = 'https://${storage.name}.queue.${environment().suffixes.storage}'
