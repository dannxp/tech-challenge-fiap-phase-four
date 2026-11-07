package br.com.fiap.feedback.infrastructure.cosmos;

import br.com.fiap.feedback.domain.Avaliacao;
import br.com.fiap.feedback.domain.Urgencia;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Conversão entre a entidade de domínio {@link Avaliacao} e o documento
 * persistido no Cosmos DB {@link AvaliacaoDocument}.
 *
 * <p>O TTL (que expira o item em ~1 ano) é um detalhe do mecanismo de
 * persistência, por isso passa por aqui e não pela entidade.</p>
 */
public final class AvaliacaoDocumentMapper {

    private AvaliacaoDocumentMapper() {
    }

    /** Converte a entidade para o formato de storage, aplicando o TTL informado. */
    public static AvaliacaoDocument paraDocumento(Avaliacao avaliacao, int ttlSegundos) {
        AvaliacaoDocument documento = new AvaliacaoDocument();
        documento.setId(avaliacao.id());
        documento.setDescricao(avaliacao.descricao());
        documento.setNota(avaliacao.nota());
        documento.setUrgencia(avaliacao.urgencia().name());
        documento.setDataEnvio(avaliacao.dataEnvio().toString());
        documento.setData(avaliacao.data().toString());
        documento.setTtl(ttlSegundos);
        return documento;
    }

    /** Reconstrói a entidade a partir do documento lido do Cosmos DB. */
    public static Avaliacao paraEntidade(AvaliacaoDocument documento) {
        return new Avaliacao(
                documento.getId(),
                documento.getDescricao(),
                documento.getNota(),
                Urgencia.valueOf(documento.getUrgencia()),
                Instant.parse(documento.getDataEnvio()),
                LocalDate.parse(documento.getData()));
    }
}
