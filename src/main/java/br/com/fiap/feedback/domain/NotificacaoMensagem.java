package br.com.fiap.feedback.domain;

/**
 * Mensagem enviada para a fila de notificações.
 *
 * <p>Carrega todos os dados necessários para montar o e-mail, inclusive em
 * retentativas, sem precisar consultar o Cosmos DB novamente.</p>
 */
public record NotificacaoMensagem(
        String id,
        String descricao,
        int nota,
        String urgencia,
        String dataEnvio,
        int tentativa
) {

    public static NotificacaoMensagem de(Avaliacao avaliacao, int tentativa) {
        return new NotificacaoMensagem(
                avaliacao.getId(),
                avaliacao.getDescricao(),
                avaliacao.getNota(),
                avaliacao.getUrgencia().name(),
                avaliacao.getDataEnvio(),
                tentativa);
    }

    public NotificacaoMensagem comTentativa(int novaTentativa) {
        return new NotificacaoMensagem(id, descricao, nota, urgencia, dataEnvio, novaTentativa);
    }

    public Urgencia urgenciaEnum() {
        return Urgencia.valueOf(urgencia);
    }
}
