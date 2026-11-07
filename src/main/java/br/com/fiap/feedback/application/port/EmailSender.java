package br.com.fiap.feedback.application.port;

import br.com.fiap.feedback.application.dto.EmailMensagem;

public interface EmailSender {

    void enviar(EmailMensagem mensagem);
}
