package br.com.fiap.feedback.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A entidade de domínio é auto-validável: a factory {@code criar} aplica as
 * regras de negócio (descrição, nota) e deriva id, urgência e datas.
 */
class AvaliacaoTest {

    private static final Instant AGORA = Instant.parse("2026-11-06T23:10:00Z");
    private static final ZoneId FUSO_SP = ZoneId.of("America/Sao_Paulo");
    private static final Clock CLOCK = Clock.fixed(AGORA, ZoneOffset.UTC);

    @Test
    void criaValidadaComIdUrgenciaEDataLocal() {
        Avaliacao avaliacao = Avaliacao.criar("  Aula excelente  ", 9, CLOCK, FUSO_SP);

        assertThat(avaliacao.descricao()).isEqualTo("Aula excelente");
        assertThat(avaliacao.nota()).isEqualTo(9);
        assertThat(avaliacao.urgencia()).isEqualTo(Urgencia.BAIXA);
        assertThat(avaliacao.id()).isNotBlank();
        assertThat(avaliacao.dataEnvio()).isEqualTo(AGORA);
        assertThat(avaliacao.data()).isEqualTo("2026-11-06"); // 20:10 em SP, dia 06
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejeitaDescricaoObrigatoria(String descricao) {
        assertThatThrownBy(() -> Avaliacao.criar(descricao, 5, CLOCK, FUSO_SP))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("descricao");
    }

    @Test
    void rejeitaDescricaoAcimaDoLimite() {
        assertThatThrownBy(() ->
                Avaliacao.criar("a".repeat(Avaliacao.DESCRICAO_MAXIMA + 1), 5, CLOCK, FUSO_SP))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining(String.valueOf(Avaliacao.DESCRICAO_MAXIMA));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 11})
    void rejeitaNotaForaDoIntervalo(int nota) {
        assertThatThrownBy(() -> Avaliacao.criar("Teste", nota, CLOCK, FUSO_SP))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("entre 0 e 10");
    }

    @Test
    void rejeitaNotaNula() {
        assertThatThrownBy(() -> Avaliacao.criar("Teste", null, CLOCK, FUSO_SP))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("nota");
    }

    @Test
    void notaCriticaNaFronteiraInferior() {
        Avaliacao avaliacao = Avaliacao.criar("Péssima", 0, CLOCK, FUSO_SP);
        assertThat(avaliacao.urgencia()).isEqualTo(Urgencia.CRITICA);
        assertThat(avaliacao.urgencia().isCritica()).isTrue();
    }
}
