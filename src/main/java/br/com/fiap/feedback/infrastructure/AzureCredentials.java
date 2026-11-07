package br.com.fiap.feedback.infrastructure;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.identity.ManagedIdentityCredentialBuilder;

/**
 * Fábrica de credenciais para os SDKs do Azure.
 *
 * <p>No Function App (App Service/Flex) as variáveis {@code IDENTITY_ENDPOINT} e
 * {@code IDENTITY_HEADER} são injetadas pelo host. Nesse cenário usamos
 * {@link ManagedIdentityCredentialBuilder} diretamente, evitando a sondagem do
 * IMDS (169.254.169.254), que não é alcançável no sandbox e gera exceções
 * inofensivas no Application Insights.</p>
 *
 * <p>Fora do Azure (desenvolvimento local), cai no
 * {@link DefaultAzureCredentialBuilder} (Azure CLI, variáveis de ambiente etc.).</p>
 */
public final class AzureCredentials {

    private AzureCredentials() {
    }

    public static TokenCredential padrao() {
        if (System.getenv("IDENTITY_ENDPOINT") != null
                || System.getenv("MSI_ENDPOINT") != null) {
            return new ManagedIdentityCredentialBuilder().build();
        }
        return new DefaultAzureCredentialBuilder().build();
    }
}
