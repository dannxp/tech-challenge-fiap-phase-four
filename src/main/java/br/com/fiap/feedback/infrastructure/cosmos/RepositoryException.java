package br.com.fiap.feedback.infrastructure.cosmos;

/**
 * Falha de acesso ao Cosmos DB.
 */
public class RepositoryException extends RuntimeException {

    public RepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
