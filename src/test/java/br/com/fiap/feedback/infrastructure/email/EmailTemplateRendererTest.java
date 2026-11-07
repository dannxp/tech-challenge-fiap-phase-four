package br.com.fiap.feedback.infrastructure.email;

import br.com.fiap.feedback.application.dto.EmailMensagem;
import br.com.fiap.feedback.domain.Avaliacao;
import br.com.fiap.feedback.domain.NotificacaoMensagem;
import br.com.fiap.feedback.domain.RelatorioSemanal;
import br.com.fiap.feedback.domain.Urgencia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplateRendererTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private EmailTemplateRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new EmailTemplateRenderer(ZONE);
    }

    @Test
    void montaEmailCriticoComDadosObrigatorios() {
        NotificacaoMensagem mensagem = new NotificacaoMensagem(
                "abc-123", "Aula <b>confusa</b>", 2, "CRITICA",
                "2026-11-06T23:10:00Z", 0);

        EmailMensagem email = renderer.criarEmailCritico(
                mensagem, List.of("admin@example.com"));

        assertThat(email.assunto()).isEqualTo("[CRÍTICO] Nova avaliação com nota 2");
        assertThat(email.html())
                .contains("Aula &lt;b&gt;confusa&lt;/b&gt;")
                .contains("CRITICA")
                .contains("2/10")
                .contains("06/11/2026 20:10")
                .contains("abc-123")
                .doesNotContain("<b>confusa</b>");
        assertThat(email.texto())
                .contains("Descrição: Aula <b>confusa</b>")
                .contains("Urgência:  CRITICA")
                .contains("Data:      06/11/2026 20:10");
    }

    @Test
    void montaRelatorioComContagensEDetalhamento() {
        LocalDate inicio = LocalDate.of(2026, 10, 31);
        LocalDate fim = LocalDate.of(2026, 11, 6);

        Map<LocalDate, Long> porDia = new LinkedHashMap<>();
        porDia.put(inicio, 0L);
        porDia.put(LocalDate.of(2026, 11, 4), 1L);
        porDia.put(fim, 1L);

        Map<Urgencia, Long> porUrgencia = new LinkedHashMap<>();
        porUrgencia.put(Urgencia.CRITICA, 1L);
        porUrgencia.put(Urgencia.MEDIA, 0L);
        porUrgencia.put(Urgencia.BAIXA, 1L);

        List<Avaliacao> avaliacoes = List.of(
                new Avaliacao("1", "Aula confusa", 2, Urgencia.CRITICA,
                        Instant.parse("2026-11-04T12:00:00Z"), LocalDate.of(2026, 11, 4)),
                new Avaliacao("2", "Aula ótima", 9, Urgencia.BAIXA,
                        Instant.parse("2026-11-06T12:00:00Z"), LocalDate.of(2026, 11, 6)));

        RelatorioSemanal relatorio = new RelatorioSemanal(
                inicio, fim, Instant.parse("2026-11-06T12:00:00Z"),
                2, 5.5, porDia, porUrgencia, avaliacoes);

        EmailMensagem email = renderer.criarEmailRelatorio(
                relatorio, List.of("admin@example.com"));

        assertThat(email.assunto())
                .isEqualTo("Relatório semanal de feedbacks — 31/10/2026 a 06/11/2026");
        assertThat(email.html())
                .contains("31/10/2026 a 06/11/2026")
                .contains("5,5")
                .contains(">2<")
                .contains("CRITICA")
                .contains("Aula confusa")
                .contains("04/11/2026 09:00");
        assertThat(email.texto())
                .contains("Total de avaliações: 2")
                .contains("Média das notas: 5,5 / 10")
                .contains("Aula confusa");
    }

    @Test
    void escapaHtml() {
        assertThat(EmailTemplateRenderer.escapeHtml("<script>alert(\"x\")</script>"))
                .isEqualTo("&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt;");
    }
}
