package br.com.fiap.feedback.application.port;

import java.util.Map;

public interface Telemetry {

    void evento(String nome, Map<String, String> propriedades);

    void metrica(String nome, double valor);
}
