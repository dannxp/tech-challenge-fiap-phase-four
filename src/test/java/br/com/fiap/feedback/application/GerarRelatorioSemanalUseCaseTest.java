package br.com.fiap.feedback.application;

import br.com.fiap.feedback.application.dto.EmailMensagem;
import br.com.fiap.feedback.application.port.AvaliacaoRepository;
import br.com.fiap.feedback.application.port.EmailSender;
import br.com.fiap.feedback.application.port.Telemetry;
import br.com.fiap.feedback.domain.Avaliacao;
import br.com.fiap.feedback.domain.RelatorioSemanal;
import br.com.fiap.feedback.domain.Urgencia;
import br.com.fiap.feedback.infrastructure.email.EmailTemplateRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GerarRelatorioSemanalUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-11-06T12:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    @Mock
    private AvaliacaoRepository repository;
    @Mock
    private EmailSender emailSender;
    @Mock
    private Telemetry telemetry;

    private GerarRelatorioSemanalUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GerarRelatorioSemanalUseCase(
                repository,
                emailSender,
                new EmailTemplateRenderer(ZONE),
                new AdminEmails(List.of("admin@example.com")),
                telemetry,
                Clock.fixed(AGORA, ZoneOffset.UTC),
                ZONE);
    }

    @Test
    void agregaUltimosSeteDiasEEnviaEmail() {
        when(repository.buscarPorPeriodo(
                LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 6)))
                .thenReturn(List.of(
                        avaliacao("1", "2026-11-04T12:00:00Z", "2026-11-04", 2, Urgencia.CRITICA),
                        avaliacao("2", "2026-11-05T12:00:00Z", "2026-11-05", 6, Urgencia.MEDIA),
                        avaliacao("3", "2026-11-06T12:00:00Z", "2026-11-06", 10, Urgencia.BAIXA)));

        RelatorioSemanal relatorio = useCase.gerar();

        assertThat(relatorio.inicio()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(relatorio.fim()).isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(relatorio.geradoEm()).isEqualTo(AGORA);
        assertThat(relatorio.total()).isEqualTo(3);
        assertThat(relatorio.media()).isCloseTo(6.0, within(0.001));
        assertThat(relatorio.avaliacoes()).extracting(Avaliacao::id)
                .containsExactly("3", "2", "1");

        assertThat(relatorio.porDia()).hasSize(7);
        assertThat(relatorio.porDia().get(LocalDate.of(2026, 10, 31))).isZero();
        assertThat(relatorio.porDia().get(LocalDate.of(2026, 11, 4))).isEqualTo(1);
        assertThat(relatorio.porDia().get(LocalDate.of(2026, 11, 6))).isEqualTo(1);

        assertThat(relatorio.porUrgencia())
                .containsEntry(Urgencia.CRITICA, 1L)
                .containsEntry(Urgencia.MEDIA, 1L)
                .containsEntry(Urgencia.BAIXA, 1L);

        ArgumentCaptor<EmailMensagem> captor = ArgumentCaptor.forClass(EmailMensagem.class);
        verify(emailSender).enviar(captor.capture());
        EmailMensagem email = captor.getValue();
        assertThat(email.destinatarios()).containsExactly("admin@example.com");
        assertThat(email.assunto()).contains("31/10/2026 a 06/11/2026");
        assertThat(email.html()).contains("6,0", "CRITICA", "06/11/2026");
        assertThat(email.texto()).contains("Total de avaliações: 3");

        verify(telemetry).evento(eq("relatorio_gerado"), any());
    }

    @Test
    void enviaRelatorioMesmoSemAvaliacoes() {
        when(repository.buscarPorPeriodo(any(), any())).thenReturn(List.of());

        RelatorioSemanal relatorio = useCase.gerar();

        assertThat(relatorio.total()).isZero();
        assertThat(relatorio.media()).isZero();
        assertThat(relatorio.porDia()).hasSize(7).allSatisfy((dia, qtd) ->
                assertThat(qtd).isZero());

        ArgumentCaptor<EmailMensagem> captor = ArgumentCaptor.forClass(EmailMensagem.class);
        verify(emailSender).enviar(captor.capture());
        assertThat(captor.getValue().html()).contains("Nenhuma avaliação no período");
    }

    @Test
    void naoEnviaEmailQuandoNaoHaAdminConfigurado() {
        GerarRelatorioSemanalUseCase semAdmin = new GerarRelatorioSemanalUseCase(
                repository, emailSender, new EmailTemplateRenderer(ZONE),
                new AdminEmails(List.of()), telemetry,
                Clock.fixed(AGORA, ZoneOffset.UTC), ZONE);
        when(repository.buscarPorPeriodo(any(), any())).thenReturn(List.of());

        RelatorioSemanal relatorio = semAdmin.gerar();

        assertThat(relatorio.total()).isZero();
        verify(emailSender, never()).enviar(any());
    }

    private Avaliacao avaliacao(String id, String dataEnvio, String data,
                                int nota, Urgencia urgencia) {
        return new Avaliacao(
                id, "Aula " + id, nota, urgencia,
                Instant.parse(dataEnvio), LocalDate.parse(data));
    }
}
