package br.com.fiap.feedback.application.port;

import br.com.fiap.feedback.application.dto.EmailMensagem;
import br.com.fiap.feedback.domain.NotificacaoMensagem;
import br.com.fiap.feedback.domain.RelatorioSemanal;

import java.util.List;

public interface EmailFactory {

    EmailMensagem criarEmailCritico(NotificacaoMensagem mensagem, List<String> destinatarios);

    EmailMensagem criarEmailRelatorio(RelatorioSemanal relatorio, List<String> destinatarios);
}
