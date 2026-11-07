package br.com.fiap.feedback.infrastructure.queue;

import br.com.fiap.feedback.application.port.NotificacaoQueue;
import br.com.fiap.feedback.config.FeedbackProperties;
import br.com.fiap.feedback.domain.NotificacaoMensagem;
import br.com.fiap.feedback.infrastructure.AzureCredentials;
import com.azure.core.util.Context;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Publica mensagens na fila do Azure Storage.
 *
 * <p>Em produção usa Managed Identity; localmente aceita uma connection string
 * do Azurite via {@code STORAGE_CONNECTION_STRING}.</p>
 */
@Component
public class StorageQueueNotificacaoQueue implements NotificacaoQueue {

    private static final Logger log = LoggerFactory.getLogger(StorageQueueNotificacaoQueue.class);

    private final FeedbackProperties properties;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean filaVerificada = new AtomicBoolean(false);

    private volatile QueueClient queueClient;

    public StorageQueueNotificacaoQueue(FeedbackProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void enfileirar(NotificacaoMensagem mensagem) {
        enviar(mensagem, null);
    }

    @Override
    public void enfileirarComAtraso(NotificacaoMensagem mensagem, Duration atraso) {
        enviar(mensagem, atraso);
    }

    private void enviar(NotificacaoMensagem mensagem, Duration atraso) {
        String body = serializar(mensagem);
        QueueClient client = client();
        garantirFila(client);

        try {
            if (atraso == null || atraso.isZero() || atraso.isNegative()) {
                client.sendMessage(body);
            } else {
                client.sendMessageWithResponse(body, atraso, null, null, Context.NONE);
            }
            log.info("Mensagem da avaliação {} enfileirada{}",
                    mensagem.id(),
                    atraso == null ? "" : " com atraso de " + atraso.toSeconds() + "s");
        } catch (RuntimeException e) {
            throw new QueueException(
                    "Falha ao enfileirar a notificação da avaliação " + mensagem.id() + ".", e);
        }
    }

    private String serializar(NotificacaoMensagem mensagem) {
        try {
            return objectMapper.writeValueAsString(mensagem);
        } catch (JsonProcessingException e) {
            throw new QueueException("Falha ao serializar a mensagem de notificação.", e);
        }
    }

    private void garantirFila(QueueClient client) {
        if (filaVerificada.compareAndSet(false, true)) {
            try {
                boolean criada = client.createIfNotExists();
                log.info("Fila '{}' verificada (criada={}).", client.getQueueName(), criada);
            } catch (RuntimeException e) {
                // A fila já é criada pelo Bicep. Se não tivermos permissão de
                // create, seguimos e deixamos o sendMessage reportar o erro real.
                log.warn("Não foi possível verificar/criar a fila '{}': {}",
                        client.getQueueName(), e.getMessage());
            }
        }
    }

    private QueueClient client() {
        QueueClient atual = this.queueClient;
        if (atual == null) {
            synchronized (this) {
                atual = this.queueClient;
                if (atual == null) {
                    atual = criarClient();
                    this.queueClient = atual;
                }
            }
        }
        return atual;
    }

    private QueueClient criarClient() {
        QueueClientBuilder builder = new QueueClientBuilder()
                .queueName(properties.queueName());

        if (properties.storageConnectionString() != null
                && !properties.storageConnectionString().isBlank()) {
            log.info("Storage Queue: usando connection string local.");
            builder.connectionString(properties.storageConnectionString());
        } else if (properties.storageQueueEndpoint() != null
                && !properties.storageQueueEndpoint().isBlank()) {
            log.info("Storage Queue: usando Managed Identity em {}.",
                    properties.storageQueueEndpoint());
            builder.endpoint(properties.storageQueueEndpoint())
                    .credential(AzureCredentials.padrao());
        } else {
            throw new QueueException(
                    "STORAGE_QUEUE_ENDPOINT/STORAGE_CONNECTION_STRING não configurado.",
                    new IllegalStateException("Configuração ausente"));
        }

        return builder.buildClient();
    }
}
