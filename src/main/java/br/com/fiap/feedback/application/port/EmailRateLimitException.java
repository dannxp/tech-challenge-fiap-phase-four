package br.com.fiap.feedback.application.port;

import java.time.Duration;

/**
 * O provedor de e-mail recusou o envio por limite de vazão (HTTP 429).
 */
public class EmailRateLimitException extends RuntimeException {

    private final Duration retryAfter;

    public EmailRateLimitException(Duration retryAfter, Throwable cause) {
        super("Limite de envio de e-mails atingido; tentar novamente em "
                + retryAfter.toSeconds() + "s", cause);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
