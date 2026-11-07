package br.com.fiap.feedback.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrgenciaCalculatorTest {

    @ParameterizedTest
    @CsvSource({
            "0, CRITICA",
            "1, CRITICA",
            "3, CRITICA",
            "4, MEDIA",
            "5, MEDIA",
            "6, MEDIA",
            "7, BAIXA",
            "9, BAIXA",
            "10, BAIXA"
    })
    void calculaUrgenciaPelaNota(int nota, Urgencia esperada) {
        assertThat(UrgenciaCalculator.calcular(nota)).isEqualTo(esperada);
    }

    @Test
    void rejeitaNotaNula() {
        assertThatThrownBy(() -> UrgenciaCalculator.validarNota(null))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("nota");
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

    @Test
    void aceitaDescricaoERemoveEspacosDasBordas() {
        assertThat(UrgenciaCalculator.validarDescricao("  aula boa  "))
                .isEqualTo("aula boa");
    }

    @Test
    void rejeitaDescricaoVazia() {
        assertThatThrownBy(() -> UrgenciaCalculator.validarDescricao("   "))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("descricao");
    }

    @Test
    void rejeitaDescricaoAcimaDoLimite() {
        String descricao = "a".repeat(UrgenciaCalculator.DESCRICAO_MAXIMA + 1);
        assertThatThrownBy(() -> UrgenciaCalculator.validarDescricao(descricao))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("1000");
    }
}
