<#
.SYNOPSIS
    Provisiona a infraestrutura, grava o segredo do ACS no Key Vault e publica o
    código do Function App.

.DESCRIPTION
    Uso típico (primeira vez):

        .\scripts\deploy.ps1 -AdminEmails "admin@exemplo.com" `
                             -AcsSender "DoNotReply@<guid>.azurecomm.net"

    A connection string do ACS é solicitada de forma segura (não aparece na tela)
    ou pode ser lida da variável de ambiente ACS_CONNECTION_STRING.

    Nenhum segredo é gravado no repositório. A connection string é enviada
    diretamente para o Key Vault.

.EXAMPLE
    $env:ACS_CONNECTION_STRING = "<connection-string>"
    .\scripts\deploy.ps1 -AdminEmails "admin@exemplo.com" `
                         -AcsSender "DoNotReply@<guid>.azurecomm.net"
#>
[CmdletBinding()]
param(
    [string]$ResourceGroup = 'rg-feedback-fase4',

    [string]$Location = 'southafricanorth',

    [string]$KeyVaultLocation = 'southafricanorth',

    [string]$MonitoringLocation = 'southafricanorth',

    [string]$CosmosLocation = 'southafricanorth',

    [string]$SubscriptionId = '',

    [string]$AdminEmails = $env:ADMIN_EMAILS,

    [string]$AcsSender = $env:ACS_SENDER,

    # Se vazio, o script pergunta de forma segura ou usa $env:ACS_CONNECTION_STRING.
    [string]$AcsConnectionString = $env:ACS_CONNECTION_STRING,

    [string]$RelatorioCron = '0 0 8 * * MON',

    [string]$Timezone = 'America/Sao_Paulo',

    [bool]$EnableCosmosFreeTier = $true,

    [switch]$SkipPackage,

    [switch]$SkipCodeDeploy
)

$ErrorActionPreference = 'Stop'

function Resolve-AzCli {
    $command = Get-Command az -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }
    $candidates = @(
        'C:\Program Files\Microsoft SDKs\Azure\CLI2\wbin\az.cmd',
        'C:\Program Files (x86)\Microsoft SDKs\Azure\CLI2\wbin\az.cmd',
        (Join-Path $env:LOCALAPPDATA 'Programs\Azure CLI\az.cmd')
    )
    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            return $candidate
        }
    }
    throw 'Azure CLI (az) não encontrado. Instale em https://aka.ms/azcli e abra um novo terminal.'
}

function Invoke-Az {
    param(
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )
    & $script:AzCli @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Falha ao executar: az $($Arguments -join ' ')"
    }
}

# O Compress-Archive do Windows grava os nomes das entradas com '\', o que quebra
# o host Linux do Functions (as funções não são indexadas). Este zip usa '/'.
function New-FunctionAppPackage {
    param(
        [Parameter(Mandatory = $true)][string]$SourcePath,
        [Parameter(Mandatory = $true)][string]$DestinationPath
    )
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem

    if (Test-Path $DestinationPath) {
        Remove-Item $DestinationPath -Force
    }

    $source = (Resolve-Path $SourcePath).Path
    $archive = [System.IO.Compression.ZipFile]::Open($DestinationPath, 'Create')
    try {
        Get-ChildItem $source -Recurse -File |
            Where-Object { $_.Name -ne 'local.settings.json' } |
            ForEach-Object {
                $entryName = $_.FullName.Substring($source.Length + 1).Replace('\', '/')
                $entry = $archive.CreateEntry($entryName, [System.IO.Compression.CompressionLevel]::Optimal)
                $output = $entry.Open()
                try {
                    $input = [System.IO.File]::OpenRead($_.FullName)
                    try {
                        $input.CopyTo($output)
                    } finally {
                        $input.Dispose()
                    }
                } finally {
                    $output.Dispose()
                }
            }
    } finally {
        $archive.Dispose()
    }
}

$script:AzCli = Resolve-AzCli
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    if ([string]::IsNullOrWhiteSpace($AdminEmails)) {
        throw 'Informe -AdminEmails (ou defina a variável ADMIN_EMAILS).'
    }
    if ([string]::IsNullOrWhiteSpace($AcsSender)) {
        throw 'Informe -AcsSender (ou defina a variável ACS_SENDER).'
    }

    Write-Host '==> Verificando login no Azure' -ForegroundColor Cyan
    try {
        Invoke-Az -Arguments @('account', 'show') | Out-Null
    } catch {
        throw 'Sessão Azure inválida. Rode "az login" e tente novamente.'
    }

    if (-not [string]::IsNullOrWhiteSpace($SubscriptionId)) {
        Write-Host "==> Selecionando assinatura $SubscriptionId" -ForegroundColor Cyan
        Invoke-Az -Arguments @('account', 'set', '--subscription', $SubscriptionId) | Out-Null
    }

    Write-Host "==> Verificando Resource Group $ResourceGroup (recursos em $Location)" -ForegroundColor Cyan
    $rgExists = & $script:AzCli group exists --name $ResourceGroup
    if ($rgExists -ne 'true') {
        Invoke-Az -Arguments @('group', 'create', '--name', $ResourceGroup, '--location', $Location, '--only-show-errors') | Out-Null
    }

    $existingFunctionAppName = & $script:AzCli functionapp list `
        --resource-group $ResourceGroup `
        --query "[?starts_with(name, 'func-feedback-fase4')].name | [0]" `
        --output tsv
    $planName = 'asp-feedback-fase4'
    $deployRoles = $true
    if (-not [string]::IsNullOrWhiteSpace($existingFunctionAppName)) {
        $existingPlanId = & $script:AzCli functionapp list `
            --resource-group $ResourceGroup `
            --query "[?name=='$existingFunctionAppName'].serverFarmId | [0]" `
            --output tsv
        if (-not [string]::IsNullOrWhiteSpace($existingPlanId)) {
            $planName = Split-Path $existingPlanId -Leaf
            Write-Host "==> Reutilizando o plano do Function App existente: $planName" -ForegroundColor Yellow
        }
        $existingPrincipalId = & $script:AzCli functionapp identity show `
            --resource-group $ResourceGroup `
            --name $existingFunctionAppName `
            --query principalId `
            --output tsv
        if (-not [string]::IsNullOrWhiteSpace($existingPrincipalId)) {
            $existingRoles = @(& $script:AzCli role assignment list `
                --assignee $existingPrincipalId `
                --all `
                --query "[?roleDefinitionName=='Storage Queue Data Contributor' || roleDefinitionName=='Key Vault Secrets User'].roleDefinitionName" `
                --output tsv)
            if ($existingRoles.Count -ge 2) {
                $deployRoles = $false
                Write-Host '==> Reutilizando as atribuições RBAC existentes.' -ForegroundColor Yellow
            }
        }
    }

    if (-not $SkipPackage) {
        Write-Host '==> Compilando e empacotando (mvn clean package)' -ForegroundColor Cyan
        & mvn -B clean package
        if ($LASTEXITCODE -ne 0) {
            throw 'Falha no build Maven.'
        }
    }

    $deploymentName = "feedback-{0}" -f (Get-Date -Format 'yyyyMMddHHmmss')
    Write-Host "==> Provisionando infraestrutura (deployment $deploymentName)" -ForegroundColor Cyan
    $outputsJson = & $script:AzCli deployment group create `
        --resource-group $ResourceGroup `
        --name $deploymentName `
        --template-file 'infra/main.bicep' `
        --parameters "adminEmails=$AdminEmails" `
                     "acsSender=$AcsSender" `
                     "relatorioCron=$RelatorioCron" `
                     "timezone=$Timezone" `
                     "location=$Location" `
                     "keyVaultLocation=$KeyVaultLocation" `
                     "monitoringLocation=$MonitoringLocation" `
                     "cosmosLocation=$CosmosLocation" `
                     "planName=$planName" `
                     "deployRoles=$($deployRoles.ToString().ToLower())" `
                     "enableCosmosFreeTier=$($EnableCosmosFreeTier.ToString().ToLower())" `
        --query 'properties.outputs' `
        --output json `
        --only-show-errors

    if ($LASTEXITCODE -ne 0) {
        throw 'Falha no provisionamento da infraestrutura.'
    }

    $outputs = $outputsJson | ConvertFrom-Json
    $functionAppName = $outputs.functionAppName.value
    $functionAppUrl = $outputs.functionAppUrl.value
    $keyVaultName = $outputs.keyVaultName.value
    $acsSecretName = $outputs.acsSecretName.value

    Write-Host "    Function App : $functionAppName" -ForegroundColor Green
    Write-Host "    URL          : $functionAppUrl" -ForegroundColor Green
    Write-Host "    Key Vault    : $keyVaultName" -ForegroundColor Green

    if ([string]::IsNullOrWhiteSpace($AcsConnectionString)) {
        Write-Host '==> Connection string do ACS não informada.' -ForegroundColor Yellow
        $secure = Read-Host '    Cole a connection string do ACS (não será exibida)' -AsSecureString
        $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        try {
            $AcsConnectionString = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
        } finally {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
        }
    }

    if (-not [string]::IsNullOrWhiteSpace($AcsConnectionString)) {
        Write-Host '==> Concedendo ao usuário atual acesso de escrita no Key Vault' -ForegroundColor Cyan
        $vaultId = & $script:AzCli keyvault show --name $keyVaultName --resource-group $ResourceGroup --query id --output tsv
        $userId = & $script:AzCli ad signed-in-user show --query id --output tsv 2>$null
        if (-not [string]::IsNullOrWhiteSpace($userId)) {
            & $script:AzCli role assignment create `
                --role 'Key Vault Secrets Officer' `
                --assignee-object-id $userId `
                --assignee-principal-type User `
                --scope $vaultId `
                --only-show-errors 2>$null | Out-Null
            Start-Sleep -Seconds 10
        }

        Write-Host "==> Gravando segredo '$acsSecretName' no Key Vault" -ForegroundColor Cyan
        $gravado = $false
        for ($tentativa = 1; $tentativa -le 5; $tentativa++) {
            & $script:AzCli keyvault secret set `
                --vault-name $keyVaultName `
                --name $acsSecretName `
                --value $AcsConnectionString `
                --only-show-errors | Out-Null
            if ($LASTEXITCODE -eq 0) {
                $gravado = $true
                break
            }
            Write-Host "    Tentativa $tentativa falhou (propagação de RBAC). Aguardando 15s..." -ForegroundColor Yellow
            Start-Sleep -Seconds 15
        }
        if (-not $gravado) {
            throw "Não foi possível gravar o segredo no Key Vault '$keyVaultName'. Verifique sua permissão de 'Key Vault Secrets Officer'."
        }
        Write-Host '    Segredo gravado com sucesso.' -ForegroundColor Green
    } else {
        Write-Host '    AVISO: segredo do ACS não gravado. O envio de e-mail não funcionará até gravá-lo.' -ForegroundColor Yellow
    }

    if (-not $SkipCodeDeploy) {
        $staging = Join-Path $projectRoot 'target/azure-functions/func-feedback-fase4'
        if (-not (Test-Path $staging)) {
            throw "Pasta de staging não encontrada: $staging. Rode sem -SkipPackage."
        }

        $zipPath = Join-Path $projectRoot 'target/functionapp.zip'
        Write-Host '==> Compactando o pacote do Functions' -ForegroundColor Cyan
        New-FunctionAppPackage -SourcePath $staging -DestinationPath $zipPath

        Write-Host '==> Publicando código em ' -NoNewline -ForegroundColor Cyan
        Write-Host $functionAppName -ForegroundColor Cyan
        Invoke-Az -Arguments @(
            'functionapp', 'deployment', 'source', 'config-zip',
            '--resource-group', $ResourceGroup,
            '--name', $functionAppName,
            '--src', $zipPath,
            '--only-show-errors'
        ) | Out-Null

        Write-Host '==> Reiniciando o Function App' -ForegroundColor Cyan
        Invoke-Az -Arguments @(
            'functionapp', 'restart',
            '--resource-group', $ResourceGroup,
            '--name', $functionAppName,
            '--only-show-errors'
        ) | Out-Null
    }

    Write-Host '==> Lendo a function key padrão' -ForegroundColor Cyan
    $functionKey = & $script:AzCli functionapp keys list `
        --resource-group $ResourceGroup `
        --name $functionAppName `
        --query 'functionKeys.default' `
        --output tsv

    Write-Host ''
    Write-Host 'Deploy concluído.' -ForegroundColor Green
    Write-Host "  URL do endpoint : $functionAppUrl/api/avaliacao"
    Write-Host "  Function App    : $functionAppName"
    Write-Host "  Resource Group  : $ResourceGroup"
    Write-Host ''
    Write-Host 'Smoke test:' -ForegroundColor Cyan
    Write-Host "  .\scripts\smoke-test.ps1 -ResourceGroup $ResourceGroup -FunctionAppName $functionAppName -FunctionKey $functionKey"
    Write-Host ''
    Write-Host 'Exemplo de chamada:' -ForegroundColor Cyan
    Write-Host "  curl -X POST `"$functionAppUrl/api/avaliacao?code=$functionKey`" -H `"Content-Type: application/json`" -d '{`"descricao`":`"Aula excelente`",`"nota`":9}'"
}
finally {
    Pop-Location
}
