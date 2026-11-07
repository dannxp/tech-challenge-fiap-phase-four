package br.com.fiap.feedback.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrgenciaCalculatorTest {

    @Test
    void notasBaixasSaoCriticas() {
        assertThat(UrgenciaCalculator.calcular(0)).isEqualTo(Urgencia.CRITICA);
        assertThat(UrgenciaCalculator.calcular(1)).isEqualTo(Urgencia.CRITICA);
        assertThat(UrgenciaCalculator.calcular(3)).isEqualTo(Urgencia.CRITICA);
    }

    @Test
    void notasMediasSaoMedia() {
        assertThat(UrgenciaCalculator.calcular(4)).isEqualTo(Urgencia.MEDIA);
        assertThat(UrgenciaCalculator.calcular(6)).isEqualTo(Urgencia.MEDIA);
    }

    @Test
    void notasAltasSaoBaixas() {
        assertThat(UrgenciaCalculator.calcular(7)).isEqualTo(Urgencia.BAIXA);
        assertThat(UrgenciaCalculator.calcular(9)).isEqualTo(Urgencia.BAIXA);
        assertThat(UrgenciaCalculator.calcular(10)).isEqualTo(Urgencia.BAIXA);
    }

    @Test
    void rejeitaNotaNegativa() {
        assertThatThrownBy(() -> UrgenciaCalculator.calcular(-1))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("entre 0 e 10");
    }

    @Test
    void rejeitaNotaAcimaDeDez() {
        assertThatThrownBy(() -> UrgenciaCalculator.calcular(11))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("entre 0 e 10");
    }
}
