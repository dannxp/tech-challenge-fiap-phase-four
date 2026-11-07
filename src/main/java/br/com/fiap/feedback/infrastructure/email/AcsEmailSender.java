package br.com.fiap.feedback.infrastructure.email;

import br.com.fiap.feedback.application.dto.EmailMensagem;
import br.com.fiap.feedback.application.port.EmailRateLimitException;
import br.com.fiap.feedback.application.port.EmailSendException;
import br.com.fiap.feedback.application.port.EmailSender;
import br.com.fiap.feedback.config.FeedbackProperties;
import com.azure.communication.email.EmailClient;
import com.azure.communication.email.EmailClientBuilder;
import com.azure.communication.email.models.EmailAddress;
import com.azure.communication.email.models.EmailMessage;
import com.azure.communication.email.models.EmailSendResult;
import com.azure.communication.email.models.EmailSendStatus;
import com.azure.core.exception.HttpResponseException;
import com.azure.core.util.polling.SyncPoller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Envia e-mails transacionais pelo Azure Communication Services.
 *
 * <p>O cliente é criado sob demanda a partir da connection string que chega
 * do Key Vault (app setting {@code ACS_CONNECTION_STRING}).</p>
 */
@Component
public class AcsEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(AcsEmailSender.class);
    private static final Duration RETRY_PADRAO = Duration.ofSeconds(60);
    private static final Duration RETRY_MAXIMO = Duration.ofSeconds(600);

    private final FeedbackProperties properties;

    private volatile EmailClient client;

    public AcsEmailSender(FeedbackProperties properties) {
        this.properties = properties;
    }

    @Override
    public void enviar(EmailMensagem mensagem) {
        if (mensagem.destinatarios() == null || mensagem.destinatarios().isEmpty()) {
            throw new EmailSendException("Nenhum destinatário configurado em ADMIN_EMAILS.");
        }
        if (properties.acsSender() == null || properties.acsSender().isBlank()) {
            throw new EmailSendException("ACS_SENDER não configurado.");
        }

        EmailMessage email = new EmailMessage()
                .setSenderAddress(properties.acsSender())
                .setSubject(mensagem.assunto())
                .setBodyHtml(mensagem.html())
                .setBodyPlainText(mensagem.texto())
                .setToRecipients(mensagem.destinatarios().stream()
                        .map(EmailAddress::new)
                        .toList());

        try {
            SyncPoller<EmailSendResult, EmailSendResult> poller = client().beginSend(email);
            EmailSendResult resultado = poller.waitForCompletion().getValue();

            if (resultado == null || !EmailSendStatus.SUCCEEDED.equals(resultado.getStatus())) {
                String detalhe = resultado == null
                        ? "sem resultado"
                        : String.valueOf(resultado.getStatus());
                throw new EmailSendException("ACS não confirmou o envio do e-mail: " + detalhe);
            }

            log.info("E-mail '{}' enviado para {} (id ACS {})",
                    mensagem.assunto(), mensagem.destinatarios(), resultado.getId());
        } catch (HttpResponseException e) {
            int status = e.getResponse() == null ? 0 : e.getResponse().getStatusCode();
            if (status == 429) {
                throw new EmailRateLimitException(extrairRetryAfter(e), e);
            }
            throw new EmailSendException(
                    "ACS retornou HTTP " + status + " ao enviar o e-mail.", e);
        } catch (EmailSendException | EmailRateLimitException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new EmailSendException("Falha inesperada ao enviar e-mail pelo ACS.", e);
        }
    }

    private Duration extrairRetryAfter(HttpResponseException e) {
        String header = e.getResponse() == null
                ? null
                : e.getResponse().getHeaderValue("Retry-After");
        if (header == null || header.isBlank()) {
            return RETRY_PADRAO;
        }
        try {
            Duration duracao = Duration.ofSeconds(Long.parseLong(header.trim()));
            if (duracao.isNegative() || duracao.isZero()) {
                return RETRY_PADRAO;
            }
            return duracao.compareTo(RETRY_MAXIMO) > 0 ? RETRY_MAXIMO : duracao;
        } catch (NumberFormatException ex) {
            log.warn("Retry-After inválido recebido do ACS: '{}'. Usando {}s.",
                    header, RETRY_PADRAO.toSeconds());
            return RETRY_PADRAO;
        }
    }

    private EmailClient client() {
        EmailClient atual = this.client;
        if (atual == null) {
            synchronized (this) {
                atual = this.client;
                if (atual == null) {
                    if (properties.acsConnectionString() == null
                            || properties.acsConnectionString().isBlank()) {
                        throw new EmailSendException("ACS_CONNECTION_STRING não configurado.");
                    }
                    atual = new EmailClientBuilder()
                            .connectionString(properties.acsConnectionString())
                            .buildClient();
                    this.client = atual;
                }
            }
        }
        return atual;
    }
}
