package br.com.fiap.feedback.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Resultado agregado do relatório semanal.
 */
public record RelatorioSemanal(
        LocalDate inicio,
        LocalDate fim,
        Instant geradoEm,
        long total,
        double media,
        Map<LocalDate, Long> porDia,
        Map<Urgencia, Long> porUrgencia,
        List<Avaliacao> avaliacoes
) {
}
