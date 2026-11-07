<#
.SYNOPSIS
    Executa o smoke test do endpoint POST /api/avaliacao.

.DESCRIPTION
    Testa:
      1. Avaliação não crítica (201, sem e-mail)
      2. Avaliação crítica (201, dispara e-mail)
      3. Payload inválido (400)
      4. Requisição sem function key (401)

    A function key pode ser informada ou obtida automaticamente via Azure CLI.

.EXAMPLE
    .\scripts\smoke-test.ps1 -ResourceGroup rg-feedback-fase4
#>
[CmdletBinding()]
param(
    [string]$ResourceGroup = 'rg-feedback-fase4',

    [string]$FunctionAppName,

    [string]$FunctionKey,

    [string]$BaseUrl
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
    return $null
}

$az = Resolve-AzCli

if ([string]::IsNullOrWhiteSpace($FunctionAppName)) {
    if (-not $az) {
        throw 'Informe -FunctionAppName (Azure CLI não encontrado para descobrir automaticamente).'
    }
    $FunctionAppName = & $az functionapp list `
        --resource-group $ResourceGroup `
        --query "[?starts_with(name, 'func-feedback-fase4')].name | [0]" `
        --output tsv
    if ([string]::IsNullOrWhiteSpace($FunctionAppName)) {
        throw "Nenhum Function App 'func-feedback-fase4*' encontrado em $ResourceGroup."
    }
}

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
    if (-not $az) {
        throw 'Informe -BaseUrl (Azure CLI não encontrado para descobrir o hostname).'
    }
    # No Flex Consumption o 'functionapp show' pode retornar propriedades nulas;
    # o hostname é obtido pela listagem.
    $hostName = & $az functionapp list `
        --resource-group $ResourceGroup `
        --query "[?name=='$FunctionAppName'].defaultHostName | [0]" `
        --output tsv
    if ([string]::IsNullOrWhiteSpace($hostName)) {
        throw "Não foi possível obter o hostname de $FunctionAppName."
    }
    $BaseUrl = "https://$hostName"
}

if ([string]::IsNullOrWhiteSpace($FunctionKey)) {
    if (-not $az) {
        throw 'Informe -FunctionKey (Azure CLI não encontrado).'
    }
    $FunctionKey = & $az functionapp keys list `
        --resource-group $ResourceGroup `
        --name $FunctionAppName `
        --query 'functionKeys.default' `
        --output tsv
    if ([string]::IsNullOrWhiteSpace($FunctionKey)) {
        throw "Não foi possível obter a function key de $FunctionAppName."
    }
}

$urlComChave = "$BaseUrl/api/avaliacao"
$urlSemChave = "$BaseUrl/api/avaliacao"

function Invoke-Avaliacao {
    param(
        [string]$Url,
        [string]$Body,
        [switch]$ComChave
    )

    $headers = @{ 'Content-Type' = 'application/json' }
    if ($ComChave) {
        $headers['x-functions-key'] = $FunctionKey
    }
    try {
        $response = Invoke-WebRequest -Method Post -Uri $Url -Headers $headers -Body $Body -UseBasicParsing
        return [pscustomobject]@{
            Status = [int]$response.StatusCode
            Corpo  = $response.Content
        }
    } catch {
        $status = 0
        if ($_.Exception.Response -ne $null) {
            $status = [int]$_.Exception.Response.StatusCode.value__
        }
        $corpo = ''
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
            $corpo = $_.ErrorDetails.Message
        }
        return [pscustomobject]@{
            Status = $status
            Corpo  = $corpo
        }
    }
}

$resultados = @()

Write-Host "==> $urlComChave" -ForegroundColor Cyan

$r1 = Invoke-Avaliacao -Url $urlComChave -ComChave -Body '{"descricao":"Smoke test: aula excelente, muito clara","nota":9}'
$resultados += [pscustomobject]@{
    Teste  = 'Avaliação não crítica (nota 9)'
    Espera = '201'
    Obtido = $r1.Status
    Ok     = ($r1.Status -eq 201)
    Corpo  = $r1.Corpo
}

Start-Sleep -Seconds 2

$r2 = Invoke-Avaliacao -Url $urlComChave -ComChave -Body '{"descricao":"Smoke test: não entendi nada da aula","nota":2}'
$resultados += [pscustomobject]@{
    Teste  = 'Avaliação crítica (nota 2, dispara e-mail)'
    Espera = '201'
    Obtido = $r2.Status
    Ok     = ($r2.Status -eq 201)
    Corpo  = $r2.Corpo
}

$r3 = Invoke-Avaliacao -Url $urlComChave -ComChave -Body '{"descricao":"","nota":11}'
$resultados += [pscustomobject]@{
    Teste  = 'Payload inválido (nota 11 e descrição vazia)'
    Espera = '400'
    Obtido = $r3.Status
    Ok     = ($r3.Status -eq 400)
    Corpo  = $r3.Corpo
}

$r4 = Invoke-Avaliacao -Url $urlSemChave -Body '{"descricao":"sem chave","nota":5}'
$resultados += [pscustomobject]@{
    Teste  = 'Sem function key'
    Espera = '401'
    Obtido = $r4.Status
    Ok     = ($r4.Status -eq 401 -or $r4.Status -eq 403)
    Corpo  = $r4.Corpo
}

Write-Host ''
$resultados | Format-Table -AutoSize -Property Teste, Espera, Obtido, Ok

$falhas = @($resultados | Where-Object { -not $_.Ok })
if ($falhas.Count -gt 0) {
    Write-Host "Falhas: $($falhas.Count)" -ForegroundColor Red
    $falhas | ForEach-Object {
        Write-Host " - $($_.Teste): esperado $($_.Espera), obtido $($_.Obtido)" -ForegroundColor Red
        if ($_.Corpo) {
            Write-Host "   $($_.Corpo)"
        }
    }
    exit 1
}

Write-Host 'Smoke test concluído com sucesso.' -ForegroundColor Green
Write-Host 'Verifique a caixa de entrada dos admins para o e-mail da avaliação crítica.' -ForegroundColor Yellow
