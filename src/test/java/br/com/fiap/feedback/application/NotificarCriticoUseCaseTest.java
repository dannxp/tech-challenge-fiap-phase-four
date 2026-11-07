package br.com.fiap.feedback.application;

import br.com.fiap.feedback.application.dto.EmailMensagem;
import br.com.fiap.feedback.application.port.EmailFactory;
import br.com.fiap.feedback.application.port.EmailRateLimitException;
import br.com.fiap.feedback.application.port.EmailSendException;
import br.com.fiap.feedback.application.port.EmailSender;
import br.com.fiap.feedback.application.port.NotificacaoQueue;
import br.com.fiap.feedback.application.port.Telemetry;
import br.com.fiap.feedback.domain.NotificacaoMensagem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificarCriticoUseCaseTest {

    @Mock
    private EmailSender emailSender;
    @Mock
    private NotificacaoQueue queue;
    @Mock
    private EmailFactory emailFactory;
    @Mock
    private Telemetry telemetry;

    private NotificarCriticoUseCase useCase;
    private NotificacaoMensagem mensagem;

    @BeforeEach
    void setUp() {
        useCase = new NotificarCriticoUseCase(
                emailSender, queue, emailFactory,
                new AdminEmails(List.of("admin@example.com")), telemetry);

        mensagem = new NotificacaoMensagem(
                "id-1", "Aula confusa", 2, "CRITICA",
                "2026-11-06T23:10:00Z", 0);

        lenient().when(emailFactory.criarEmailCritico(any(), any()))
                .thenReturn(new EmailMensagem(
                        List.of("admin@example.com"), "assunto", "<p>html</p>", "texto"));
    }

    @Test
    void enviaEmailComSucesso() {
        useCase.notificar(mensagem);

        verify(emailSender).enviar(any(EmailMensagem.class));
        verify(telemetry).evento(eq("notificacao_enviada"), any());
        verify(queue, never()).enfileirarComAtraso(any(), any());
    }

    @Test
    void reenfileiraComAtrasoQuandoAcsRetorna429() {
        doThrow(new EmailRateLimitException(Duration.ofSeconds(30), null))
                .when(emailSender).enviar(any());

        useCase.notificar(mensagem);

        ArgumentCaptor<NotificacaoMensagem> captor =
                ArgumentCaptor.forClass(NotificacaoMensagem.class);
        verify(queue).enfileirarComAtraso(captor.capture(), eq(Duration.ofSeconds(30)));
        assertThat(captor.getValue().tentativa()).isEqualTo(1);
        verify(telemetry).evento(eq("notificacao_reagendada"), any());
    }

    @Test
    void desisteAposCincoTentativas() {
        NotificacaoMensagem ultima = mensagem.comTentativa(5);
        doThrow(new EmailRateLimitException(Duration.ofSeconds(30), null))
                .when(emailSender).enviar(any());

        assertThatThrownBy(() -> useCase.notificar(ultima))
                .isInstanceOf(EmailSendException.class)
                .hasMessageContaining("5 tentativas");

        verify(queue, never()).enfileirarComAtraso(any(), any());
    }

    @Test
    void falhaQuandoNaoHaAdminConfigurado() {
        NotificarCriticoUseCase semAdmin = new NotificarCriticoUseCase(
                emailSender, queue, emailFactory,
                new AdminEmails(List.of()), telemetry);

        assertThatThrownBy(() -> semAdmin.notificar(mensagem))
                .isInstanceOf(EmailSendException.class)
                .hasMessageContaining("ADMIN_EMAILS");
    }
}
