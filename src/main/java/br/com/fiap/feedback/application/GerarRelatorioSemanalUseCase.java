package br.com.fiap.feedback.application;

import br.com.fiap.feedback.application.dto.EmailMensagem;
import br.com.fiap.feedback.application.port.AvaliacaoRepository;
import br.com.fiap.feedback.application.port.EmailFactory;
import br.com.fiap.feedback.application.port.EmailSender;
import br.com.fiap.feedback.application.port.Telemetry;
import br.com.fiap.feedback.domain.Avaliacao;
import br.com.fiap.feedback.domain.RelatorioSemanal;
import br.com.fiap.feedback.domain.Urgencia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Caso de uso 3: consolidar os últimos 7 dias e enviar o relatório semanal.
 *
 * <p>A janela é sempre "hoje - 6 dias" até "hoje" no fuso de São Paulo,
 * contada a partir do momento do disparo (não é segunda a domingo).</p>
 */
@Service
public class GerarRelatorioSemanalUseCase {

    private static final Logger log = LoggerFactory.getLogger(GerarRelatorioSemanalUseCase.class);

    private final AvaliacaoRepository repository;
    private final EmailSender emailSender;
    private final EmailFactory emailFactory;
    private final AdminEmails adminEmails;
    private final Telemetry telemetry;
    private final Clock clock;
    private final ZoneId zoneId;

    public GerarRelatorioSemanalUseCase(AvaliacaoRepository repository,
                                        EmailSender emailSender,
                                        EmailFactory emailFactory,
                                        AdminEmails adminEmails,
                                        Telemetry telemetry,
                                        Clock clock,
                                        ZoneId zoneId) {
        this.repository = repository;
        this.emailSender = emailSender;
        this.emailFactory = emailFactory;
        this.adminEmails = adminEmails;
        this.telemetry = telemetry;
        this.clock = clock;
        this.zoneId = zoneId;
    }

    public RelatorioSemanal gerar() {
        LocalDate hoje = LocalDate.now(clock.withZone(zoneId));
        LocalDate inicio = hoje.minusDays(6);

        List<Avaliacao> avaliacoes = repository.buscarPorPeriodo(inicio, hoje);

        // Mais recentes primeiro (null-safe por robustez, nunca deve ocorrer).
        List<Avaliacao> ordenadas = new ArrayList<>(avaliacoes);
        ordenadas.sort(Comparator.comparing(
                Avaliacao::dataEnvio,
                Comparator.nullsLast(Comparator.reverseOrder())));

        Map<LocalDate, Long> porDia = new LinkedHashMap<>();
        for (LocalDate dia = inicio; !dia.isAfter(hoje); dia = dia.plusDays(1)) {
            porDia.put(dia, 0L);
        }

        Map<Urgencia, Long> porUrgencia = new EnumMap<>(Urgencia.class);
        for (Urgencia urgencia : Urgencia.values()) {
            porUrgencia.put(urgencia, 0L);
        }

        long somaNotas = 0;
        for (Avaliacao avaliacao : ordenadas) {
            LocalDate dia = avaliacao.data();
            if (dia != null && porDia.containsKey(dia)) {
                porDia.merge(dia, 1L, Long::sum);
            }
            porUrgencia.merge(avaliacao.urgencia(), 1L, Long::sum);
            somaNotas += avaliacao.nota();
        }

        long total = ordenadas.size();
        double media = total == 0 ? 0.0 : (double) somaNotas / total;
        Instant geradoEm = clock.instant();

        RelatorioSemanal relatorio = new RelatorioSemanal(
                inicio, hoje, geradoEm, total, media, porDia, porUrgencia, ordenadas);

        log.info("Relatório semanal gerado: período {} a {}, total={}, media={}",
                inicio, hoje, total, String.format("%.1f", media));

        if (adminEmails.vazio()) {
            log.warn("ADMIN_EMAILS não configurado; relatório não será enviado.");
            return relatorio;
        }

        EmailMensagem email = emailFactory.criarEmailRelatorio(relatorio, adminEmails.emails());
        emailSender.enviar(email);

        telemetry.evento("relatorio_gerado", Map.of(
                "inicio", inicio.toString(),
                "fim", hoje.toString(),
                "total", String.valueOf(total),
                "media", String.format("%.1f", media)));
        telemetry.metrica("relatorio_total_avaliacoes", total);

        return relatorio;
    }
}
