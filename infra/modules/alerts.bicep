// Alertas básicos de operação: action group com os e-mails dos admins e um
// alerta de erros HTTP 5xx no Function App.

@description('Nome do action group.')
param actionGroupName string

@description('E-mails dos administradores separados por vírgula.')
param adminEmails string

@description('Resource ID do Function App monitorado.')
param functionAppId string

@description('Tags aplicadas aos recursos.')
param tags object = {}

var emails = [for (email, i) in split(adminEmails, ','): {
  name: 'admin${i}'
  emailAddress: trim(email)
}]

resource actionGroup 'Microsoft.Insights/actionGroups@2023-01-01' = {
  name: actionGroupName
  location: 'global'
  tags: tags
  properties: {
    groupShortName: 'fbfase4'
    enabled: true
    emailReceivers: emails
  }
}

resource http5xxAlert 'Microsoft.Insights/metricAlerts@2018-03-01' = {
  name: 'alert-func-http5xx'
  location: 'global'
  tags: tags
  properties: {
    description: 'O Function App respondeu com erro HTTP 5xx.'
    severity: 2
    enabled: true
    scopes: [
      functionAppId
    ]
    evaluationFrequency: 'PT5M'
    windowSize: 'PT15M'
    criteria: {
      'odata.type': 'Microsoft.Azure.Monitor.SingleResourceMultipleMetricCriteria'
      allOf: [
        {
          name: 'Http5xx'
          metricName: 'Http5xx'
          metricNamespace: 'Microsoft.Web/sites'
          operator: 'GreaterThan'
          threshold: 0
          timeAggregation: 'Total'
          criterionType: 'StaticThresholdCriterion'
        }
      ]
    }
    actions: [
      {
        actionGroupId: actionGroup.id
      }
    ]
  }
}

@description('Resource ID do action group.')
output actionGroupId string = actionGroup.id
