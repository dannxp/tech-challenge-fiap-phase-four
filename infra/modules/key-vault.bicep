// Key Vault com RBAC e soft delete. O segredo acs-connection-string é gravado
// pelo script de deploy (nunca pelo Bicep/repositório).

@description('Nome globalmente único do Key Vault (3-24 caracteres).')
param name string

@description('Região do recurso.')
param location string

@description('Tags aplicadas ao recurso.')
param tags object = {}

resource vault 'Microsoft.KeyVault/vaults@2023-07-01' = {
  name: name
  location: location
  tags: tags
  properties: {
    sku: {
      family: 'A'
      name: 'standard'
    }
    tenantId: tenant().tenantId
    enableRbacAuthorization: true
    enableSoftDelete: true
    softDeleteRetentionInDays: 7
    enablePurgeProtection: true
    publicNetworkAccess: 'Enabled'
    networkAcls: {
      defaultAction: 'Allow'
      bypass: 'AzureServices'
    }
  }
}

@description('Resource ID do Key Vault.')
output id string = vault.id

@description('Nome do Key Vault.')
output name string = vault.name

@description('URI do Key Vault.')
output uri string = vault.properties.vaultUri
