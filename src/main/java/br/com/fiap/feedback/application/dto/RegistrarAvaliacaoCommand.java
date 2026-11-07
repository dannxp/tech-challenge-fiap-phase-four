package br.com.fiap.feedback.application.dto;

/**
 * Payload recebido em POST /api/avaliacao.
 */
public record RegistrarAvaliacaoCommand(
        String descricao,
        Integer nota
) {
}
