// Cosmos DB for NoSQL.
// - Free tier (1.000 RU/s + 25 GB) quando disponível na assinatura.
// - Autenticação por chave local desabilitada: apenas Entra ID / Managed Identity.
// - Container com partition key /data e TTL de 365 dias (LGPD).

@description('Nome globalmente único da conta Cosmos DB.')
param name string

@description('Região da conta.')
param location string

@description('Nome do database.')
param databaseName string = 'feedback'

@description('Nome do container.')
param containerName string = 'avaliacoes'

@description('Habilita o free tier. Só pode existir uma conta free tier por assinatura.')
param enableFreeTier bool = true

@description('Tags aplicadas aos recursos.')
param tags object = {}

resource account 'Microsoft.DocumentDB/databaseAccounts@2024-05-15' = {
  name: name
  location: location
  kind: 'GlobalDocumentDB'
  tags: tags
  properties: {
    databaseAccountOfferType: 'Standard'
    locations: [
      {
        locationName: location
        failoverPriority: 0
        isZoneRedundant: false
      }
    ]
    consistencyPolicy: {
      defaultConsistencyLevel: 'Session'
    }
    enableFreeTier: enableFreeTier
    enableAutomaticFailover: false
    disableLocalAuth: true
    publicNetworkAccess: 'Enabled'
    minimalTlsVersion: 'Tls12'
    capabilities: []
  }
}

resource database 'Microsoft.DocumentDB/databaseAccounts/sqlDatabases@2024-05-15' = {
  parent: account
  name: databaseName
  properties: {
    resource: {
      id: databaseName
    }
  }
}

resource container 'Microsoft.DocumentDB/databaseAccounts/sqlDatabases/containers@2024-05-15' = {
  parent: database
  name: containerName
  properties: {
    resource: {
      id: containerName
      partitionKey: {
        paths: [
          '/data'
        ]
        kind: 'Hash'
      }
      defaultTtl: 31536000
      indexingPolicy: {
        indexingMode: 'consistent'
        automatic: true
        includedPaths: [
          {
            path: '/*'
          }
        ]
        excludedPaths: [
          {
            path: '/"_etag"/?'
          }
        ]
      }
    }
  }
}

@description('Resource ID da conta Cosmos DB.')
output id string = account.id

@description('Nome da conta Cosmos DB.')
output name string = account.name

@description('Endpoint documental (HTTPS).')
output endpoint string = account.properties.documentEndpoint
