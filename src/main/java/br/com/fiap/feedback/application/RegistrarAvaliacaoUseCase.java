package br.com.fiap.feedback.application;

import br.com.fiap.feedback.application.dto.AvaliacaoResponse;
import br.com.fiap.feedback.application.dto.RegistrarAvaliacaoCommand;
import br.com.fiap.feedback.application.port.AvaliacaoRepository;
import br.com.fiap.feedback.application.port.NotificacaoQueue;
import br.com.fiap.feedback.application.port.Telemetry;
import br.com.fiap.feedback.domain.Avaliacao;
import br.com.fiap.feedback.domain.NotificacaoMensagem;
import br.com.fiap.feedback.domain.Urgencia;
import br.com.fiap.feedback.domain.UrgenciaCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

/**
 * Caso de uso 1: receber, validar, classificar e persistir uma avaliação.
 */
@Service
public class RegistrarAvaliacaoUseCase {

    private static final Logger log = LoggerFactory.getLogger(RegistrarAvaliacaoUseCase.class);
    private static final int TTL_SEGUNDOS = 365 * 24 * 60 * 60;

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
            throw new br.com.fiap.feedback.domain.ValidacaoException("Corpo da requisição é obrigatório.");
        }

        String descricao = UrgenciaCalculator.validarDescricao(command.descricao());
        int nota = UrgenciaCalculator.validarNota(command.nota());
        Urgencia urgencia = UrgenciaCalculator.calcular(nota);

        Instant agora = clock.instant();
        String dataEnvio = agora.toString();
        String data = LocalDate.ofInstant(agora, zoneId).toString();

        Avaliacao avaliacao = new Avaliacao(
                UUID.randomUUID().toString(),
                descricao,
                nota,
                urgencia,
                dataEnvio,
                data,
                TTL_SEGUNDOS);

        repository.salvar(avaliacao);
        log.info("Avaliação {} salva: nota={}, urgencia={}, data={}",
                avaliacao.getId(), nota, urgencia, data);

        if (urgencia.isCritica()) {
            notificacaoQueue.enfileirar(NotificacaoMensagem.de(avaliacao, 0));
            log.info("Avaliação crítica {} enfileirada para notificação", avaliacao.getId());
        }

        telemetry.evento("avaliacao_recebida", Map.of(
                "nota", String.valueOf(nota),
                "urgencia", urgencia.name()));
        telemetry.metrica("avaliacoes_total", 1);
        if (urgencia.isCritica()) {
            telemetry.metrica("criticas_total", 1);
        }

        return new AvaliacaoResponse(avaliacao.getId(), urgencia.name(), dataEnvio);
    }
}
