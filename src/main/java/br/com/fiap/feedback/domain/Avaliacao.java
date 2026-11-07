package br.com.fiap.feedback.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Avaliação de aula — entidade de domínio.
 *
 * <p>Valor imutável que não conhece Jackson nem Cosmos DB: a representação
 * persistida fica em {@code infrastructure.cosmos.AvaliacaoDocument}, e o TTL
 * é política do repositório, não da entidade.</p>
 *
 * <p>{@code dataEnvio} é o instante de recebimento (UTC) e {@code data} é o dia
 * local (fuso configurado) usado para agrupar o relatório semanal e como
 * partition key lógica.</p>
 *
 * @param id        identificador único (UUID)
 * @param descricao texto enviado pelo aluno (trim, até {@link #DESCRICAO_MAXIMA} caracteres)
 * @param nota      nota de {@link #NOTA_MINIMA} a {@link #NOTA_MAXIMA}
 * @param urgencia  urgência derivada da nota
 * @param dataEnvio instante de recebimento (UTF)
 * @param data      dia local (fuso configurado) do recebimento
 */
public record Avaliacao(
        String id,
        String descricao,
        int nota,
        Urgencia urgencia,
        Instant dataEnvio,
        LocalDate data) {

    public static final int NOTA_MINIMA = 0;
    public static final int NOTA_MAXIMA = 10;
    public static final int DESCRICAO_MAXIMA = 1000;

    /**
     * Cria uma avaliação validada, com id, instante de envio e dia local
     * derivados do relógio/fuso informados.
     *
     * @throws ValidacaoException se descrição ou nota forem inválidos
     */
    public static Avaliacao criar(String descricao, Integer nota, Clock clock, ZoneId zoneId) {
        String descricaoValida = validarDescricao(descricao);
        int notaValida = validarNota(nota);

        Instant agora = clock.instant();
        return new Avaliacao(
                UUID.randomUUID().toString(),
                descricaoValida,
                notaValida,
                UrgenciaCalculator.calcular(notaValida),
                agora,
                LocalDate.ofInstant(agora, zoneId));
    }

    /**
     * @throws ValidacaoException se nula, em branco ou acima do limite de caracteres
     */
    static String validarDescricao(String descricao) {
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

    /**
     * @throws ValidacaoException se nula ou fora do intervalo {@link #NOTA_MINIMA}–{@link #NOTA_MAXIMA}
     */
    static int validarNota(Integer nota) {
        if (nota == null) {
            throw new ValidacaoException("O campo 'nota' é obrigatório.");
        }
        if (nota < NOTA_MINIMA || nota > NOTA_MAXIMA) {
            throw new ValidacaoException(
                    "O campo 'nota' deve ser um inteiro entre " + NOTA_MINIMA + " e " + NOTA_MAXIMA + ".");
        }
        return nota;
    }
}
