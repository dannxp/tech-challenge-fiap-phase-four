package br.com.fiap.feedback.infrastructure.cosmos;

import br.com.fiap.feedback.application.port.AvaliacaoRepository;
import br.com.fiap.feedback.config.FeedbackProperties;
import br.com.fiap.feedback.domain.Avaliacao;
import br.com.fiap.feedback.infrastructure.AzureCredentials;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import br.com.fiap.feedback.infrastructure.AzureCredentials;
import com.azure.cosmos.models.SqlQuerySpec;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Repositório Cosmos DB (SQL API).
 *
 * <p>Autenticação: Managed Identity em produção; {@code COSMOS_KEY} apenas para
 * desenvolvimento local (o Bicep desabilita chaves locais na conta).</p>
 */
@Repository
public class CosmosAvaliacaoRepository implements AvaliacaoRepository {

    private static final Logger log = LoggerFactory.getLogger(CosmosAvaliacaoRepository.class);

    private final FeedbackProperties properties;

    private volatile CosmosClient client;
    private volatile CosmosContainer container;

    public CosmosAvaliacaoRepository(FeedbackProperties properties) {
        this.properties = properties;
    }

    @Override
    public void salvar(Avaliacao avaliacao) {
        try {
            container().createItem(
                    avaliacao,
                    new PartitionKey(avaliacao.getData()),
                    new CosmosItemRequestOptions());
        } catch (CosmosException e) {
            throw new RepositoryException(
                    "Falha ao salvar a avaliação " + avaliacao.getId()
                            + " no Cosmos DB (status " + e.getStatusCode() + ").", e);
        }
    }

    @Override
    public List<Avaliacao> buscarPorPeriodo(LocalDate inicio, LocalDate fim) {
        String sql = "SELECT * FROM c WHERE c.data >= @inicio AND c.data <= @fim";
        SqlQuerySpec query = new SqlQuerySpec(sql, List.of(
                new SqlParameter("@inicio", inicio.toString()),
                new SqlParameter("@fim", fim.toString())));

        List<Avaliacao> avaliacoes = new ArrayList<>();
        try {
            container()
                    .queryItems(query, new CosmosQueryRequestOptions(), Avaliacao.class)
                    .forEach(avaliacoes::add);
        } catch (CosmosException e) {
            throw new RepositoryException(
                    "Falha ao consultar avaliações no Cosmos DB (status "
                            + e.getStatusCode() + ").", e);
        }
        log.info("Consulta Cosmos DB: {} avaliação(ões) entre {} e {}",
                avaliacoes.size(), inicio, fim);
        return avaliacoes;
    }

    private CosmosContainer container() {
        CosmosContainer atual = this.container;
        if (atual == null) {
            synchronized (this) {
                atual = this.container;
                if (atual == null) {
                    atual = criarContainer();
                    this.container = atual;
                }
            }
        }
        return atual;
    }

    private CosmosContainer criarContainer() {
        if (properties.cosmosEndpoint() == null || properties.cosmosEndpoint().isBlank()) {
            throw new RepositoryException(
                    "COSMOS_ENDPOINT não configurado.", new IllegalStateException("Variável ausente"));
        }

        CosmosClientBuilder builder = new CosmosClientBuilder()
                .endpoint(properties.cosmosEndpoint())
                .contentResponseOnWriteEnabled(true);

        if (properties.cosmosKey() != null && !properties.cosmosKey().isBlank()) {
            log.info("Cosmos DB: autenticando com chave local (somente desenvolvimento).");
            builder.key(properties.cosmosKey());
        } else {
            log.info("Cosmos DB: autenticando com Managed Identity.");
            builder.credential(AzureCredentials.padrao());
        }

        this.client = builder.buildClient();
        return this.client
                .getDatabase(properties.cosmosDatabase())
                .getContainer(properties.cosmosContainer());
    }

    @PreDestroy
    public void fechar() {
        if (client != null) {
            client.close();
        }
    }
}
