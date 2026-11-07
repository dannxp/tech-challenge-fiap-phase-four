package br.com.fiap.feedback.domain;

/**
 * Regra única de cálculo da urgência de uma avaliação.
 *
 * <p>Centralizar a regra evita divergência entre o momento da gravação e
 * o momento da notificação/relatório.</p>
 */
public final class UrgenciaCalculator {

    public static final int NOTA_MINIMA = 0;
    public static final int NOTA_MAXIMA = 10;
    public static final int DESCRICAO_MAXIMA = 1000;

    private UrgenciaCalculator() {
    }

    public static Urgencia calcular(int nota) {
        validarNota(nota);
        if (nota <= 3) {
            return Urgencia.CRITICA;
        }
        if (nota <= 6) {
            return Urgencia.MEDIA;
        }
        return Urgencia.BAIXA;
    }

    public static int validarNota(Integer nota) {
        if (nota == null) {
            throw new ValidacaoException("O campo 'nota' é obrigatório.");
        }
        if (nota < NOTA_MINIMA || nota > NOTA_MAXIMA) {
            throw new ValidacaoException("O campo 'nota' deve ser um inteiro entre 0 e 10.");
        }
        return nota;
    }

    public static String validarDescricao(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            throw new ValidacaoException("O campo 'descricao' é obrigatório.");
        }
        String normalizada = descricao.trim();
        if (normalizada.length() > DESCRICAO_MAXIMA) {
            throw new ValidacaoException(
                    "O campo 'descricao' deve ter no máximo " + DESCRICAO_MAXIMA + " caracteres.");
        }
        return normalizada;
    }
}
