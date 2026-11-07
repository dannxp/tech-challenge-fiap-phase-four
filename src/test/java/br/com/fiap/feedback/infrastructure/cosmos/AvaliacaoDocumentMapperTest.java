package br.com.fiap.feedback.infrastructure.cosmos;

import br.com.fiap.feedback.domain.Avaliacao;
import br.com.fiap.feedback.domain.Urgencia;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O documento é o contrato de storage: o round-trip entidade → documento →
 * entidade não pode alterar nenhum campo (senão relatórios e enfileiramento
 * leem dados distorcidos).
 */
class AvaliacaoDocumentMapperTest {

    private static final Instant AGORA = Instant.parse("2026-11-06T23:10:00Z");
    private static final ZoneId FUSO_SP = ZoneId.of("America/Sao_Paulo");

    @Test
    void roundTripPreservaTodosOsCampos() {
        Avaliacao original = new Avaliacao(
                "id-1", "Aula confusa", 2, Urgencia.CRITICA,
                AGORA, LocalDate.of(2026, 11, 6));

        AvaliacaoDocument documento =
                AvaliacaoDocumentMapper.paraDocumento(original, 31_536_000);
        Avaliacao reconstruida = AvaliacaoDocumentMapper.paraEntidade(documento);

        assertThat(reconstruida).isEqualTo(original);

        // Formato do documento é estável (partition key /data e TTL do item).
        assertThat(documento.getData()).isEqualTo("2026-11-06");
        assertThat(documento.getTtl()).isEqualTo(31_536_000);
        assertThat(documento.getUrgencia()).isEqualTo("CRITICA");
        assertThat(documento.getDataEnvio()).isEqualTo(AGORA.toString());
        assertThat(documento.partitionKey()).isEqualTo(documento.getData());
    }

    @Test
    void aplicaTtlInformadoPeloRepositorio() {
        Avaliacao avaliacao = new Avaliacao(
                "id-2", "Aula ok", 5, Urgencia.MEDIA,
                AGORA, LocalDate.of(2026, 11, 6));

        AvaliacaoDocument documento =
                AvaliacaoDocumentMapper.paraDocumento(avaliacao, 60);

        assertThat(documento.getTtl()).isEqualTo(60);
    }

    @Test
    void factoryDoDominioGeraCamposParaOStorage() {
        Avaliacao criada = Avaliacao.criar(
                "Aula excelente", 9,
                Clock.fixed(AGORA, ZoneOffset.UTC), FUSO_SP);

        AvaliacaoDocument documento =
                AvaliacaoDocumentMapper.paraDocumento(criada, 31_536_000);

        assertThat(documento.getId()).isNotBlank();
        assertThat(documento.getDescricao()).isEqualTo("Aula excelente");
        assertThat(documento.getNota()).isEqualTo(9);
        assertThat(documento.getUrgencia()).isEqualTo("BAIXA");
    }
}
