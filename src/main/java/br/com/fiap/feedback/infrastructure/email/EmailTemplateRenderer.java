package br.com.fiap.feedback.infrastructure.email;

import br.com.fiap.feedback.application.dto.EmailMensagem;
import br.com.fiap.feedback.application.port.EmailFactory;
import br.com.fiap.feedback.domain.Avaliacao;
import br.com.fiap.feedback.domain.NotificacaoMensagem;
import br.com.fiap.feedback.domain.RelatorioSemanal;
import br.com.fiap.feedback.domain.Urgencia;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Monta os e-mails HTML/plain-text a partir dos templates em
 * {@code src/main/resources/templates}.
 */
@Component
public class EmailTemplateRenderer implements EmailFactory {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", PT_BR);
    private static final DateTimeFormatter DATA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", PT_BR);
    private static final DateTimeFormatter DIA_MES =
            DateTimeFormatter.ofPattern("dd/MM", PT_BR);

    private final String templateCritico;
    private final String templateRelatorio;
    private final ZoneId zoneId;

    public EmailTemplateRenderer(ZoneId zoneId) {
        this.zoneId = zoneId;
        this.templateCritico = lerTemplate("templates/email-critico.html");
        this.templateRelatorio = lerTemplate("templates/email-relatorio.html");
    }

    @Override
    public EmailMensagem criarEmailCritico(NotificacaoMensagem mensagem,
                                           List<String> destinatarios) {
        Urgencia urgencia = mensagem.urgenciaEnum();
        String dataFormatada = formatarDataHora(mensagem.dataEnvio());

        String html = render(templateCritico, Map.of(
                "descricao", escapeHtml(mensagem.descricao()),
                "urgencia", urgencia.name(),
                "urgenciaDescricao", escapeHtml(urgencia.descricao()),
                "nota", String.valueOf(mensagem.nota()),
                "dataEnvio", dataFormatada,
                "id", mensagem.id()));

        String texto = """
                Nova avaliação crítica recebida.

                Descrição: %s
                Urgência:  %s
                Nota:      %d/10
                Data:      %s
                ID:        %s
                """.formatted(mensagem.descricao(), urgencia.name(),
                mensagem.nota(), dataFormatada, mensagem.id());

        String assunto = "[CRÍTICO] Nova avaliação com nota " + mensagem.nota();
        return new EmailMensagem(destinatarios, assunto, html, texto);
    }

    @Override
    public EmailMensagem criarEmailRelatorio(RelatorioSemanal relatorio,
                                             List<String> destinatarios) {
        String periodo = formatarData(relatorio.inicio()) + " a " + formatarData(relatorio.fim());
        String media = formatarMedia(relatorio.media());

        String html = render(templateRelatorio, Map.of(
                "periodo", periodo,
                "total", String.valueOf(relatorio.total()),
                "media", media,
                "porDia", linhasPorDia(relatorio),
                "porUrgencia", linhasPorUrgencia(relatorio),
                "detalhamento", linhasDetalhamento(relatorio),
                "geradoEm", formatarDataHora(relatorio.geradoEm())));

        String texto = textoRelatorio(relatorio, periodo, media);
        String assunto = "Relatório semanal de feedbacks — " + periodo;
        return new EmailMensagem(destinatarios, assunto, html, texto);
    }

    private String linhasPorDia(RelatorioSemanal relatorio) {
        StringBuilder sb = new StringBuilder();
        relatorio.porDia().forEach((dia, quantidade) ->
                sb.append("<tr><td style=\"padding:6px 10px;border-bottom:1px solid #e5e7eb;\">")
                        .append(DIA_MES.format(dia))
                        .append("</td><td style=\"padding:6px 10px;border-bottom:1px solid #e5e7eb;text-align:right;\">")
                        .append(quantidade)
                        .append("</td></tr>"));
        return sb.toString();
    }

    private String linhasPorUrgencia(RelatorioSemanal relatorio) {
        StringBuilder sb = new StringBuilder();
        relatorio.porUrgencia().forEach((urgencia, quantidade) ->
                sb.append("<tr><td style=\"padding:6px 10px;border-bottom:1px solid #e5e7eb;\">")
                        .append(urgencia.name())
                        .append("</td><td style=\"padding:6px 10px;border-bottom:1px solid #e5e7eb;text-align:right;\">")
                        .append(quantidade)
                        .append("</td></tr>"));
        return sb.toString();
    }

    private String linhasDetalhamento(RelatorioSemanal relatorio) {
        if (relatorio.avaliacoes().isEmpty()) {
            return "<tr><td colspan=\"4\" style=\"padding:12px;text-align:center;color:#6b7280;\">"
                    + "Nenhuma avaliação no período.</td></tr>";
        }

        StringBuilder sb = new StringBuilder();
        for (Avaliacao avaliacao : relatorio.avaliacoes()) {
            sb.append("<tr>")
                    .append("<td style=\"padding:6px 10px;border-bottom:1px solid #e5e7eb;white-space:nowrap;\">")
                    .append(formatarDataHora(avaliacao.dataEnvio()))
                    .append("</td><td style=\"padding:6px 10px;border-bottom:1px solid #e5e7eb;\">")
                    .append(avaliacao.urgencia().name())
                    .append("</td><td style=\"padding:6px 10px;border-bottom:1px solid #e5e7eb;text-align:center;\">")
                    .append(avaliacao.nota())
                    .append("</td><td style=\"padding:6px 10px;border-bottom:1px solid #e5e7eb;\">")
                    .append(escapeHtml(avaliacao.descricao()))
                    .append("</td></tr>");
        }
        return sb.toString();
    }

    private String textoRelatorio(RelatorioSemanal relatorio, String periodo, String media) {
        StringBuilder sb = new StringBuilder();
        sb.append("Relatório semanal de feedbacks\n")
                .append("Período: ").append(periodo).append("\n")
                .append("Total de avaliações: ").append(relatorio.total()).append("\n")
                .append("Média das notas: ").append(media).append(" / 10\n\n")
                .append("Avaliações por dia:\n");
        relatorio.porDia().forEach((dia, quantidade) ->
                sb.append("  ").append(DATA.format(dia)).append("  ").append(quantidade).append("\n"));

        sb.append("\nAvaliações por urgência:\n");
        relatorio.porUrgencia().forEach((urgencia, quantidade) ->
                sb.append("  ").append(urgencia.name()).append("  ").append(quantidade).append("\n"));

        sb.append("\nDetalhamento:\n");
        if (relatorio.avaliacoes().isEmpty()) {
            sb.append("  Nenhuma avaliação no período.\n");
        } else {
            for (Avaliacao avaliacao : relatorio.avaliacoes()) {
                sb.append("  ")
                        .append(formatarDataHora(avaliacao.dataEnvio())).append(" | ")
                        .append(avaliacao.urgencia().name()).append(" | ")
                        .append(avaliacao.nota()).append("/10 | ")
                        .append(avaliacao.descricao()).append("\n");
            }
        }

        sb.append("\nGerado em: ").append(formatarDataHora(relatorio.geradoEm())).append("\n");
        return sb.toString();
    }

    private String formatarDataHora(String isoInstant) {
        if (isoInstant == null || isoInstant.isBlank()) {
            return "-";
        }
        return DATA_HORA.format(Instant.parse(isoInstant).atZone(zoneId));
    }

    private String formatarDataHora(Instant instant) {
        return instant == null ? "-" : DATA_HORA.format(instant.atZone(zoneId));
    }

    private String formatarData(LocalDate data) {
        return DATA.format(data);
    }

    private String formatarMedia(double media) {
        return String.format(PT_BR, "%.1f", media);
    }

    private String render(String template, Map<String, String> valores) {
        String resultado = template;
        for (Map.Entry<String, String> entrada : valores.entrySet()) {
            resultado = resultado.replace("{{" + entrada.getKey() + "}}", entrada.getValue());
        }
        return resultado;
    }

    static String escapeHtml(String valor) {
        if (valor == null) {
            return "";
        }
        return valor
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String lerTemplate(String caminho) {
        try (InputStream inputStream = new ClassPathResource(caminho).getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Template de e-mail não encontrado: " + caminho, e);
        }
    }
}
