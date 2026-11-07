package br.com.fiap.feedback.application;

import br.com.fiap.feedback.application.dto.EmailMensagem;
import br.com.fiap.feedback.application.port.EmailFactory;
import br.com.fiap.feedback.application.port.EmailRateLimitException;
import br.com.fiap.feedback.application.port.EmailSendException;
import br.com.fiap.feedback.application.port.EmailSender;
import br.com.fiap.feedback.application.port.NotificacaoQueue;
import br.com.fiap.feedback.application.port.Telemetry;
import br.com.fiap.feedback.domain.NotificacaoMensagem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Caso de uso 2: notificar os administradores sobre uma avaliação crítica.
 */
@Service
public class NotificarCriticoUseCase {

    private static final Logger log = LoggerFactory.getLogger(NotificarCriticoUseCase.class);
    private static final int MAX_TENTATIVAS = 5;

    private final EmailSender emailSender;
    private final NotificacaoQueue notificacaoQueue;
    private final EmailFactory emailFactory;
    private final AdminEmails adminEmails;
    private final Telemetry telemetry;

    public NotificarCriticoUseCase(EmailSender emailSender,
                                   NotificacaoQueue notificacaoQueue,
                                   EmailFactory emailFactory,
                                   AdminEmails adminEmails,
                                   Telemetry telemetry) {
        this.emailSender = emailSender;
        this.notificacaoQueue = notificacaoQueue;
        this.emailFactory = emailFactory;
        this.adminEmails = adminEmails;
        this.telemetry = telemetry;
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
            if (proximaTentativa > MAX_TENTATIVAS) {
                log.error("Avaliação {} excedeu {} tentativas por limite de vazão",
                        mensagem.id(), MAX_TENTATIVAS, e);
                telemetry.evento("notificacao_falhou", Map.of(
                        "id", mensagem.id(),
                        "motivo", "rate_limit"));
                throw new EmailSendException("Limite de vazão do ACS excedido após "
                        + MAX_TENTATIVAS + " tentativas.", e);
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
