package br.com.fiap.feedback.functions;

import br.com.fiap.feedback.application.NotificarCriticoUseCase;
import br.com.fiap.feedback.domain.NotificacaoMensagem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Função 2 — notificação de avaliação crítica.
 *
 * <p>Consome a fila, envia o e-mail pelo ACS e, em caso de 429, reenfileira
 * a mensagem com o atraso indicado pelo header Retry-After.</p>
 */
@Component
public class NotificacaoFunction {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoFunction.class);

    private final NotificarCriticoUseCase useCase;
    private final ObjectMapper objectMapper;

    public NotificacaoFunction(NotificarCriticoUseCase useCase, ObjectMapper objectMapper) {
        this.useCase = useCase;
        this.objectMapper = objectMapper;
    }

    @FunctionName("notificacao")
    public void run(
            @QueueTrigger(
                    name = "mensagem",
                    queueName = "%NOTIFICACOES_QUEUE%",
                    connection = "AzureWebJobsStorage")
            String mensagem,
            ExecutionContext context) {

        log.info("Mensagem de notificação recebida (invocation={})", context.getInvocationId());

        try {
            NotificacaoMensagem notificacao =
                    objectMapper.readValue(mensagem, NotificacaoMensagem.class);
            useCase.notificar(notificacao);
        } catch (JsonProcessingException e) {
            // Mensagem malformada não melhora com retentativa: descarta para
            // evitar a poison queue sem necessidade e registra o erro.
            log.error("Mensagem de fila inválida; descartando. Conteúdo: {}", mensagem, e);
        } catch (RuntimeException e) {
            // Erros transitórios (Cosmos, ACS, rede) sobem para o runtime do
            // Functions, que faz novas tentativas e depois move para a poison queue.
            log.error("Falha ao processar a notificação; o runtime fará retentativa.", e);
            throw e;
        }
    }
}
