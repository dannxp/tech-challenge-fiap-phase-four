package br.com.fiap.feedback.infrastructure.queue;

/**
 * Falha ao publicar/consumir mensagens na fila.
 */
public class QueueException extends RuntimeException {

    public QueueException(String message, Throwable cause) {
        super(message, cause);
    }
}
