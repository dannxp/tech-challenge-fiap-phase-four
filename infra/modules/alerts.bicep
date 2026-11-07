// Grupo de ação para alertas operacionais do projeto.

@description('Nome do action group.')
param actionGroupName string

@description('E-mails dos administradores separados por vírgula.')
param adminEmails string

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

@description('Resource ID do action group.')
output actionGroupId string = actionGroup.id
