// Role assignments da Managed Identity do Function App.
// Executado depois da criação da Function App, Cosmos DB, Storage e Key Vault.

@description('Principal ID da identidade gerenciada do Function App.')
param functionPrincipalId string

@description('Nome da conta Cosmos DB.')
param cosmosAccountName string

@description('Nome do Storage Account.')
param storageAccountName string

@description('Nome do Key Vault.')
param keyVaultName string

var cosmosDataContributorRoleId = '00000000-0000-0000-0000-000000000002'
var storageQueueDataContributorRoleId = '974c5e8b-45b9-4653-ba55-5f855dd0fb88'
var keyVaultSecretsUserRoleId = '4633458b-17de-408a-b874-0445c86b69e6'

resource cosmos 'Microsoft.DocumentDB/databaseAccounts@2024-05-15' existing = {
  name: cosmosAccountName
}

// Cosmos DB usa um tipo de role assignment próprio (data plane).
resource cosmosRole 'Microsoft.DocumentDB/databaseAccounts/sqlRoleAssignments@2024-05-15' = {
  parent: cosmos
  name: guid(cosmos.id, functionPrincipalId, cosmosDataContributorRoleId)
  properties: {
    roleDefinitionId: '${cosmos.id}/sqlRoleDefinitions/${cosmosDataContributorRoleId}'
    principalId: functionPrincipalId
    scope: cosmos.id
  }
}

resource storage 'Microsoft.Storage/storageAccounts@2023-05-01' existing = {
  name: storageAccountName
}

resource storageQueueRole 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  scope: storage
  name: guid(storage.id, functionPrincipalId, storageQueueDataContributorRoleId)
  properties: {
    roleDefinitionId: subscriptionResourceId(
      'Microsoft.Authorization/roleDefinitions',
      storageQueueDataContributorRoleId)
    principalId: functionPrincipalId
    principalType: 'ServicePrincipal'
  }
}

resource keyVault 'Microsoft.KeyVault/vaults@2023-07-01' existing = {
  name: keyVaultName
}

resource keyVaultRole 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  scope: keyVault
  name: guid(keyVault.id, functionPrincipalId, keyVaultSecretsUserRoleId)
  properties: {
    roleDefinitionId: subscriptionResourceId(
      'Microsoft.Authorization/roleDefinitions',
      keyVaultSecretsUserRoleId)
    principalId: functionPrincipalId
    principalType: 'ServicePrincipal'
  }
}

@description('Role assignment do Cosmos DB.')
output cosmosRoleAssignmentId string = cosmosRole.id

@description('Role assignment da fila do Storage.')
output storageQueueRoleAssignmentId string = storageQueueRole.id

@description('Role assignment do Key Vault.')
output keyVaultRoleAssignmentId string = keyVaultRole.id
