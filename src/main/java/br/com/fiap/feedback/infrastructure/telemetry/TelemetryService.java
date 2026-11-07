package br.com.fiap.feedback.infrastructure.telemetry;

import br.com.fiap.feedback.application.port.Telemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Telemetria baseada em logs estruturados.
 *
 * <p>O host do Azure Functions já coleta tudo o que é escrito em stdout/ILogger
 * e envia para o Application Insights. Emitir eventos e métricas como linhas
 * de log com o prefixo {@code TELEMETRIA} mantém a observabilidade sem acoplar
 * a aplicação ao SDK clássico do Application Insights (que não expõe uma API
 * pública de configuração na versão 3.x).</p>
 */
@Service
public class TelemetryService implements Telemetry {

    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    @Override
    public void evento(String nome, Map<String, String> propriedades) {
        String campos = propriedades == null || propriedades.isEmpty()
                ? ""
                : new TreeMap<>(propriedades).entrySet().stream()
                        .map(entrada -> entrada.getKey() + "=" + entrada.getValue())
                        .collect(Collectors.joining(" "));
        log.info("TELEMETRIA evento={} {}", nome, campos);
    }

    @Override
    public void metrica(String nome, double valor) {
        log.info("TELEMETRIA metrica={} valor={}", nome, valor);
    }
}
