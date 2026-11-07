package br.com.fiap.feedback.application.port;

/**
 * Falha definitiva no envio de e-mail.
 */
public class EmailSendException extends RuntimeException {

    public EmailSendException(String message) {
        super(message);
    }

    public EmailSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
