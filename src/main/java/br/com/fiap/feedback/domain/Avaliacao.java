package br.com.fiap.feedback.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Documento persistido no Cosmos DB.
 *
 * <p>O {@code dataEnvio} é UTC (ISO-8601) e o {@code data} é a data local de
 * São Paulo usada como partition key e para agrupar o relatório.</p>
 */
public class Avaliacao {

    private String id;
    private String descricao;
    private int nota;
    private Urgencia urgencia;
    private String dataEnvio;
    private String data;
    private Integer ttl;

    public Avaliacao() {
        // Jackson / Cosmos DB
    }

    public Avaliacao(String id, String descricao, int nota, Urgencia urgencia,
                     String dataEnvio, String data, Integer ttl) {
        this.id = id;
        this.descricao = descricao;
        this.nota = nota;
        this.urgencia = urgencia;
        this.dataEnvio = dataEnvio;
        this.data = data;
        this.ttl = ttl;
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

    public Urgencia getUrgencia() {
        return urgencia;
    }

    public void setUrgencia(Urgencia urgencia) {
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

    public Integer getTtl() {
        return ttl;
    }

    public void setTtl(Integer ttl) {
        this.ttl = ttl;
    }

    @JsonIgnore
    public Instant dataEnvioInstant() {
        return dataEnvio == null ? null : Instant.parse(dataEnvio);
    }

    @JsonIgnore
    public LocalDate dataLocal() {
        return data == null ? null : LocalDate.parse(data);
    }
}
