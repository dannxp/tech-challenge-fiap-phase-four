package br.com.fiap.feedback.application;

import br.com.fiap.feedback.application.dto.EmailMensagem;
import br.com.fiap.feedback.application.port.EmailFactory;
import br.com.fiap.feedback.application.port.EmailRateLimitException;
import br.com.fiap.feedback.application.port.EmailSendException;
import br.com.fiap.feedback.application.port.EmailSender;
import br.com.fiap.feedback.application.port.NotificacaoQueue;
import br.com.fiap.feedback.application.port.Telemetry;
import br.com.fiap.feedback.config.FeedbackProperties;
import br.com.fiap.feedback.domain.NotificacaoMensagem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Caso de uso 2: notificar os administradores sobre uma avaliação crítica.
 *
 * <p>Em caso de rate limit (429) do ACS, reenfileira a mensagem com o atraso
 * indicado pelo header {@code Retry-After}, até o máximo de tentativas
 * configurado em {@code feedback.notificacao-max-tentativas}.</p>
 */
@Service
public class NotificarCriticoUseCase {

    private static final Logger log = LoggerFactory.getLogger(NotificarCriticoUseCase.class);

    private final EmailSender emailSender;
    private final NotificacaoQueue notificacaoQueue;
    private final EmailFactory emailFactory;
    private final AdminEmails adminEmails;
    private final Telemetry telemetry;
    private final int maxTentativas;

    public NotificarCriticoUseCase(EmailSender emailSender,
                                   NotificacaoQueue notificacaoQueue,
                                   EmailFactory emailFactory,
                                   AdminEmails adminEmails,
                                   Telemetry telemetry,
                                   FeedbackProperties properties) {
        this.emailSender = emailSender;
        this.notificacaoQueue = notificacaoQueue;
        this.emailFactory = emailFactory;
        this.adminEmails = adminEmails;
        this.telemetry = telemetry;
        this.maxTentativas = properties.notificacaoMaxTentativas();
    }

    public void notificar(NotificacaoMensagem mensagem) {
        if (mensagem == null) {
            throw new IllegalArgumentException("Mensagem de notificação é obrigatória.");
        }
        if (adminEmails.vazio()) {
            throw new EmailSendException("ADMIN_EMAILS não configurado.");
        }

        EmailMensagem email = emailFactory.criarEmailCritico(mensagem, adminEmails.emails());

        try {
            emailSender.enviar(email);
            log.info("Notificação da avaliação {} enviada (tentativa {})",
                    mensagem.id(), mensagem.tentativa() + 1);
            telemetry.evento("notificacao_enviada", Map.of(
                    "id", mensagem.id(),
                    "urgencia", mensagem.urgencia(),
                    "tentativa", String.valueOf(mensagem.tentativa() + 1)));
        } catch (EmailRateLimitException e) {
            int proximaTentativa = mensagem.tentativa() + 1;
            if (proximaTentativa > maxTentativas) {
                log.error("Avaliação {} excedeu {} tentativas por limite de vazão",
                        mensagem.id(), maxTentativas, e);
                telemetry.evento("notificacao_falhou", Map.of(
                        "id", mensagem.id(),
                        "motivo", "rate_limit"));
                throw new EmailSendException("Limite de vazão do ACS excedido após "
                        + maxTentativas + " tentativas.", e);
            }

            log.warn("ACS retornou 429 para a avaliação {}. Reenfileirando em {}s (tentativa {})",
                    mensagem.id(), e.getRetryAfter().toSeconds(), proximaTentativa);
            notificacaoQueue.enfileirarComAtraso(
                    mensagem.comTentativa(proximaTentativa), e.getRetryAfter());
            telemetry.evento("notificacao_reagendada", Map.of(
                    "id", mensagem.id(),
                    "tentativa", String.valueOf(proximaTentativa),
                    "retry_after_segundos", String.valueOf(e.getRetryAfter().toSeconds())));
        }
    }
}
