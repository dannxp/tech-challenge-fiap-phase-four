package br.com.fiap.feedback.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Objects;

/**
 * Configuração externalizada da aplicação.
 *
 * <p>As variáveis de ambiente são mapeadas em {@code application.yml} para o
 * prefixo {@code feedback.*}. Nenhum segredo é hardcoded.</p>
 */
@ConfigurationProperties(prefix = "feedback")
public record FeedbackProperties(
        String cosmosEndpoint,
        String cosmosKey,
        String cosmosDatabase,
        String cosmosContainer,
        String storageQueueEndpoint,
        String storageConnectionString,
        String queueName,
        String acsConnectionString,
        String acsSender,
        List<String> adminEmails,
        String timezone,
        String appInsightsConnectionString
) {

    public FeedbackProperties {
        cosmosDatabase = valorOuPadrao(cosmosDatabase, "feedback");
        cosmosContainer = valorOuPadrao(cosmosContainer, "avaliacoes");
        queueName = valorOuPadrao(queueName, "avaliacoes-criticas");
        timezone = valorOuPadrao(timezone, "America/Sao_Paulo");
        adminEmails = adminEmails == null
                ? List.of()
                : adminEmails.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(email -> !email.isEmpty())
                        .toList();
    }

    private static String valorOuPadrao(String valor, String padrao) {
        return valor == null || valor.isBlank() ? padrao : valor.trim();
    }
}
