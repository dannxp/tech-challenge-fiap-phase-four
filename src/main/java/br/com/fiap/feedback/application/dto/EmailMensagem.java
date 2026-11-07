package br.com.fiap.feedback.application.dto;

import java.util.List;

/**
 * E-mail pronto para envio pelo provedor.
 */
public record EmailMensagem(
        List<String> destinatarios,
        String assunto,
        String html,
        String texto
) {
}
