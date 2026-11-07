package br.com.fiap.feedback.functions;

import br.com.fiap.feedback.application.GerarRelatorioSemanalUseCase;
import br.com.fiap.feedback.domain.RelatorioSemanal;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Função 3 — relatório semanal.
 *
 * <p>Dispara toda segunda-feira às 08:00 (America/Sao_Paulo), consolida os
 * últimos 7 dias e envia o e-mail para os administradores.</p>
 *
 * <p>O binding de timer da biblioteca Java 3.x não expõe a classe
 * {@code TimerInfo}; o payload chega como {@code String} (mesmo padrão dos
 * exemplos oficiais do Azure Functions para Java).</p>
 */
@Component
public class RelatorioFunction {

    private static final Logger log = LoggerFactory.getLogger(RelatorioFunction.class);

    private final GerarRelatorioSemanalUseCase useCase;

    public RelatorioFunction(GerarRelatorioSemanalUseCase useCase) {
        this.useCase = useCase;
    }

    @FunctionName("relatorio")
    public void run(
            @TimerTrigger(
                    name = "timerInfo",
                    schedule = "%RELATORIO_CRON%")
            String timerInfo,
            ExecutionContext context) {

        log.info("Timer do relatório disparado (invocation={}, timerInfo={})",
                context.getInvocationId(), timerInfo);

        RelatorioSemanal relatorio = useCase.gerar();

        log.info("Relatório semanal concluído: {} avaliação(ões), média {}",
                relatorio.total(), String.format(java.util.Locale.forLanguageTag("pt-BR"),
                        "%.1f", relatorio.media()));
    }
}
