package br.com.fiap.feedback.application;

import java.util.List;
import java.util.Objects;

/**
 * Destinatários das notificações e do relatório.
 */
public record AdminEmails(List<String> emails) {

    public AdminEmails {
        emails = emails == null
                ? List.of()
                : emails.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(email -> !email.isEmpty())
                        .toList();
    }

    public boolean vazio() {
        return emails.isEmpty();
    }

    public String join() {
        return String.join(",", emails);
    }
}
