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
        String appInsightsConnectionString,
        int avaliacaoTtlSegundos,
        int notificacaoMaxTentativas
) {

    /** TTL padrão das avaliações no Cosmos DB: 365 dias, em segundos. */
    static final int TTL_PADRAO = 365 * 24 * 60 * 60;
    /** Máximo padrão de retentativas de envio por rate limit do ACS. */
    static final int MAX_TENTATIVAS_PADRAO = 5;

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
        avaliacaoTtlSegundos = avaliacaoTtlSegundos <= 0 ? TTL_PADRAO : avaliacaoTtlSegundos;
        notificacaoMaxTentativas = notificacaoMaxTentativas <= 0
                ? MAX_TENTATIVAS_PADRAO
                : notificacaoMaxTentativas;
    }

    private static String valorOuPadrao(String valor, String padrao) {
        return valor == null || valor.isBlank() ? padrao : valor.trim();
    }
}
