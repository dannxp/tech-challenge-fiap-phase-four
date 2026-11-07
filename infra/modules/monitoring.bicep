// Observabilidade: Log Analytics + Application Insights (workspace-based).

@description('Nome do workspace do Log Analytics.')
param logAnalyticsName string

@description('Nome do componente do Application Insights.')
param appInsightsName string

@description('Região dos recursos.')
param location string

@description('Retenção (dias) dos logs e da telemetria.')
param retentionInDays int = 30

@description('Tags aplicadas aos recursos.')
param tags object = {}

resource workspace 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: logAnalyticsName
  location: location
  tags: tags
  properties: {
    sku: {
      name: 'PerGB2018'
    }
    retentionInDays: retentionInDays
    features: {
      enableLogAccessUsingOnlyResourcePermissions: true
    }
  }
}

resource appInsights 'Microsoft.Insights/components@2020-02-02' = {
  name: appInsightsName
  location: location
  kind: 'web'
  tags: tags
  properties: {
    Application_Type: 'web'
    WorkspaceResourceId: workspace.id
    RetentionInDays: retentionInDays
    publicNetworkAccessForIngestion: 'Enabled'
    publicNetworkAccessForQuery: 'Enabled'
  }
}

@description('Resource ID do Log Analytics.')
output logAnalyticsId string = workspace.id

@description('Resource ID do Application Insights.')
output appInsightsId string = appInsights.id

@description('Connection string do Application Insights.')
output appInsightsConnectionString string = appInsights.properties.ConnectionString
