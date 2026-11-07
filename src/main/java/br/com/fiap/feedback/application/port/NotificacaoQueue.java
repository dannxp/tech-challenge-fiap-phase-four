package br.com.fiap.feedback.application.port;

import br.com.fiap.feedback.domain.NotificacaoMensagem;

import java.time.Duration;

public interface NotificacaoQueue {

    void enfileirar(NotificacaoMensagem mensagem);

    /**
     * Reenfileira a mensagem com um atraso de visibilidade. Usado quando o
     * provedor de e-mail responde 429 (Too Many Requests).
     */
    void enfileirarComAtraso(NotificacaoMensagem mensagem, Duration atraso);
}
