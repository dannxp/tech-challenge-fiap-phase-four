package br.com.fiap.feedback.domain;

/**
 * Erro de validação do payload de entrada. Vira HTTP 400 na função.
 */
public class ValidacaoException extends RuntimeException {

    public ValidacaoException(String message) {
        super(message);
    }
}
