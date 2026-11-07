package br.com.fiap.feedback.application.dto;

/**
 * Resposta de POST /api/avaliacao.
 */
public record AvaliacaoResponse(
        String id,
        String urgencia,
        String dataEnvio
) {
}
