package br.com.fiap.feedback.application;

import br.com.fiap.feedback.application.dto.AvaliacaoResponse;
import br.com.fiap.feedback.application.dto.RegistrarAvaliacaoCommand;
import br.com.fiap.feedback.application.port.AvaliacaoRepository;
import br.com.fiap.feedback.application.port.NotificacaoQueue;
import br.com.fiap.feedback.application.port.Telemetry;
import br.com.fiap.feedback.domain.Avaliacao;
import br.com.fiap.feedback.domain.NotificacaoMensagem;
import br.com.fiap.feedback.domain.ValidacaoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Map;

/**
 * Caso de uso 1: receber, validar, classificar e persistir uma avaliação.
 */
@Service
public class RegistrarAvaliacaoUseCase {

    private static final Logger log = LoggerFactory.getLogger(RegistrarAvaliacaoUseCase.class);

    private final AvaliacaoRepository repository;
    private final NotificacaoQueue notificacaoQueue;
    private final Telemetry telemetry;
    private final Clock clock;
    private final ZoneId zoneId;

    public RegistrarAvaliacaoUseCase(AvaliacaoRepository repository,
                                     NotificacaoQueue notificacaoQueue,
                                     Telemetry telemetry,
                                     Clock clock,
                                     ZoneId zoneId) {
        this.repository = repository;
        this.notificacaoQueue = notificacaoQueue;
        this.telemetry = telemetry;
        this.clock = clock;
        this.zoneId = zoneId;
    }

    public AvaliacaoResponse registrar(RegistrarAvaliacaoCommand command) {
        if (command == null) {
            throw new ValidacaoException("Corpo da requisição é obrigatório.");
        }

        // A validação e a classificação de urgência vivem no domínio: a entidade
        // se auto-valida na factory criar().
        Avaliacao avaliacao = Avaliacao.criar(
                command.descricao(), command.nota(), clock, zoneId);

        repository.salvar(avaliacao);
        log.info("Avaliação {} salva: nota={}, urgencia={}, data={}",
                avaliacao.id(), avaliacao.nota(), avaliacao.urgencia(), avaliacao.data());

        if (avaliacao.urgencia().isCritica()) {
            notificacaoQueue.enfileirar(NotificacaoMensagem.de(avaliacao, 0));
            log.info("Avaliação crítica {} enfileirada para notificação", avaliacao.id());
        }

        telemetry.evento("avaliacao_recebida", Map.of(
                "nota", String.valueOf(avaliacao.nota()),
                "urgencia", avaliacao.urgencia().name()));
        telemetry.metrica("avaliacoes_total", 1);
        if (avaliacao.urgencia().isCritica()) {
            telemetry.metrica("criticas_total", 1);
        }

        return new AvaliacaoResponse(
                avaliacao.id(), avaliacao.urgencia().name(), avaliacao.dataEnvio().toString());
    }
}
