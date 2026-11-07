package br.com.fiap.feedback.domain;

/**
 * Urgência derivada da nota da avaliação.
 *
 * <p>0 a 3 = CRITICA (notifica os administradores), 4 a 6 = MEDIA,
 * 7 a 10 = BAIXA.</p>
 */
public enum Urgencia {

    CRITICA("Crítica"),
    MEDIA("Média"),
    BAIXA("Baixa");

    private final String descricao;

    Urgencia(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    public boolean isCritica() {
        return this == CRITICA;
    }
}
