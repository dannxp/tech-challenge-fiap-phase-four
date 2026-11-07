package br.com.fiap.feedback.infrastructure.cosmos;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Documento persistido no Cosmos DB (container {@code avaliacoes}, partição
 * {@code data}).
 *
 * <p>É o modelo de persistência — a entidade de domínio
 * {@code br.com.fiap.feedback.domain.Avaliacao} não conhece este formato. O
 * layout dos campos é estável: itens gravados antes desta separação continuam
 * legíveis.</p>
 */
public class AvaliacaoDocument {

    private String id;
    private String descricao;
    private int nota;
    private String urgencia;
    private String dataEnvio;
    private String data;
    private int ttl;

    public AvaliacaoDocument() {
        // Jackson / Cosmos DB SDK
    }

    @JsonIgnore
    public String partitionKey() {
        return data;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public int getNota() {
        return nota;
    }

    public void setNota(int nota) {
        this.nota = nota;
    }

    public String getUrgencia() {
        return urgencia;
    }

    public void setUrgencia(String urgencia) {
        this.urgencia = urgencia;
    }

    public String getDataEnvio() {
        return dataEnvio;
    }

    public void setDataEnvio(String dataEnvio) {
        this.dataEnvio = dataEnvio;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public int getTtl() {
        return ttl;
    }

    public void setTtl(int ttl) {
        this.ttl = ttl;
    }
}
