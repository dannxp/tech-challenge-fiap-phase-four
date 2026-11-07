package br.com.fiap.feedback.domain;

/**
 * Regra única de cálculo da urgência de uma avaliação.
 *
 * <p>Centralizar a regra evita divergência entre o momento da gravação e
 * o momento da notificação/relatório. A validação do payload está em
 * {@link Avaliacao} (factory {@code criar}).</p>
 */
public final class UrgenciaCalculator {

    private UrgenciaCalculator() {
    }

    /**
     * @throws ValidacaoException se a nota estiver fora do intervalo 0–10
     */
    public static Urgencia calcular(int nota) {
        Avaliacao.validarNota(nota);
        if (nota <= 3) {
            return Urgencia.CRITICA;
        }
        if (nota <= 6) {
            return Urgencia.MEDIA;
        }
        return Urgencia.BAIXA;
    }
}
