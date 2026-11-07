package br.com.fiap.feedback.functions;

import br.com.fiap.feedback.application.RegistrarAvaliacaoUseCase;
import br.com.fiap.feedback.application.dto.AvaliacaoResponse;
import br.com.fiap.feedback.application.dto.RegistrarAvaliacaoCommand;
import br.com.fiap.feedback.domain.ValidacaoException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Função 1 — POST /api/avaliacao
 *
 * <p>Recebe a avaliação, valida, calcula a urgência, grava no Cosmos DB e,
 * quando crítica, publica na fila de notificação.</p>
 */
@Component
public class AvaliacaoFunction {

    private static final Logger log = LoggerFactory.getLogger(AvaliacaoFunction.class);

    private final RegistrarAvaliacaoUseCase useCase;
    private final ObjectMapper objectMapper;

    public AvaliacaoFunction(RegistrarAvaliacaoUseCase useCase, ObjectMapper objectMapper) {
        this.useCase = useCase;
        this.objectMapper = objectMapper;
    }

    @FunctionName("avaliacao")
    public HttpResponseMessage run(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.POST},
                    authLevel = AuthorizationLevel.FUNCTION,
                    route = "avaliacao")
            HttpRequestMessage<Optional<String>> request,
            ExecutionContext context) {

        log.info("POST /api/avaliacao recebido (invocation={})", context.getInvocationId());

        try {
            String body = request.getBody().orElse(null);
            if (body == null || body.isBlank()) {
                return erro(request, HttpStatus.BAD_REQUEST,
                        "Corpo da requisição é obrigatório.");
            }

            RegistrarAvaliacaoCommand command =
                    objectMapper.readValue(body, RegistrarAvaliacaoCommand.class);

            AvaliacaoResponse response = useCase.registrar(command);
            log.info("Avaliação {} criada com urgência {}", response.id(), response.urgencia());

            return request.createResponseBuilder(HttpStatus.CREATED)
                    .header("Content-Type", "application/json")
                    .body(objectMapper.writeValueAsString(response))
                    .build();
        } catch (ValidacaoException e) {
            log.warn("Payload inválido: {}", e.getMessage());
            return erro(request, HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (JsonProcessingException e) {
            log.warn("JSON inválido recebido: {}", e.getOriginalMessage());
            return erro(request, HttpStatus.BAD_REQUEST,
                    "JSON inválido. Envie um objeto com os campos 'descricao' e 'nota'.");
        } catch (RuntimeException e) {
            log.error("Erro inesperado ao processar a avaliação", e);
            return erro(request, HttpStatus.INTERNAL_SERVER_ERROR,
                    "Erro interno ao processar a avaliação. Tente novamente mais tarde.");
        }
    }

    private HttpResponseMessage erro(HttpRequestMessage<Optional<String>> request,
                                     HttpStatus status,
                                     String mensagem) {
        try {
            String json = objectMapper.writeValueAsString(new ErroResponse(mensagem));
            return request.createResponseBuilder(status)
                    .header("Content-Type", "application/json")
                    .body(json)
                    .build();
        } catch (JsonProcessingException e) {
            return request.createResponseBuilder(status)
                    .header("Content-Type", "application/json")
                    .body("{\"erro\":\"Erro interno\"}")
                    .build();
        }
    }

    record ErroResponse(String erro) {
    }
}
