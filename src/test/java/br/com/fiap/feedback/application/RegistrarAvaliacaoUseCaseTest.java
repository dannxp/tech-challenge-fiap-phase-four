package br.com.fiap.feedback.application;

import br.com.fiap.feedback.application.dto.AvaliacaoResponse;
import br.com.fiap.feedback.application.dto.RegistrarAvaliacaoCommand;
import br.com.fiap.feedback.application.port.AvaliacaoRepository;
import br.com.fiap.feedback.application.port.NotificacaoQueue;
import br.com.fiap.feedback.application.port.Telemetry;
import br.com.fiap.feedback.domain.Avaliacao;
import br.com.fiap.feedback.domain.NotificacaoMensagem;
import br.com.fiap.feedback.domain.Urgencia;
import br.com.fiap.feedback.domain.ValidacaoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RegistrarAvaliacaoUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-11-06T23:10:00Z");

    @Mock
    private AvaliacaoRepository repository;
    @Mock
    private NotificacaoQueue queue;
    @Mock
    private Telemetry telemetry;

    private RegistrarAvaliacaoUseCase useCase;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(AGORA, ZoneOffset.UTC);
        useCase = new RegistrarAvaliacaoUseCase(
                repository, queue, telemetry,
                clock, ZoneId.of("America/Sao_Paulo"));
    }

    @Test
    void salvaAvaliacaoNaoCriticaSemEnfileirar() {
        AvaliacaoResponse response = useCase.registrar(
                new RegistrarAvaliacaoCommand("Aula excelente", 9));

        assertThat(response.urgencia()).isEqualTo("BAIXA");
        assertThat(response.dataEnvio()).isEqualTo("2026-11-06T23:10:00Z");
        assertThat(response.id()).isNotBlank();

        ArgumentCaptor<Avaliacao> captor = ArgumentCaptor.forClass(Avaliacao.class);
        verify(repository).salvar(captor.capture());
        Avaliacao salva = captor.getValue();
        assertThat(salva.descricao()).isEqualTo("Aula excelente");
        assertThat(salva.nota()).isEqualTo(9);
        assertThat(salva.urgencia()).isEqualTo(Urgencia.BAIXA);
        assertThat(salva.data()).isEqualTo("2026-11-06");

        verify(queue, never()).enfileirar(any());
    }

    @Test
    void enfileiraAvaliacaoCritica() {
        useCase.registrar(new RegistrarAvaliacaoCommand("Não entendi nada", 2));

        ArgumentCaptor<NotificacaoMensagem> captor =
                ArgumentCaptor.forClass(NotificacaoMensagem.class);
        verify(queue).enfileirar(captor.capture());
        NotificacaoMensagem mensagem = captor.getValue();

        assertThat(mensagem.urgencia()).isEqualTo("CRITICA");
        assertThat(mensagem.nota()).isEqualTo(2);
        assertThat(mensagem.tentativa()).isZero();
    }

    @Test
    void usaDataLocalDeSaoPauloComoPartitionKey() {
        // 23:10 UTC = 20:10 em São Paulo, ainda dia 06/11.
        useCase.registrar(new RegistrarAvaliacaoCommand("Teste", 5));

        ArgumentCaptor<Avaliacao> captor = ArgumentCaptor.forClass(Avaliacao.class);
        verify(repository).salvar(captor.capture());
        assertThat(captor.getValue().data()).isEqualTo("2026-11-06");
    }

    @Test
    void rejeitaNotaForaDoIntervalo() {
        assertThatThrownBy(() -> useCase.registrar(new RegistrarAvaliacaoCommand("Teste", 11)))
                .isInstanceOf(ValidacaoException.class);
        verifyNoInteractions(repository, queue);
    }

    @Test
    void rejeitaDescricaoVazia() {
        assertThatThrownBy(() -> useCase.registrar(new RegistrarAvaliacaoCommand(" ", 5)))
                .isInstanceOf(ValidacaoException.class);
        verifyNoInteractions(repository, queue);
    }

    @Test
    void rejeitaCorpoNulo() {
        assertThatThrownBy(() -> useCase.registrar(null))
                .isInstanceOf(ValidacaoException.class);
        verifyNoInteractions(repository, queue);
    }
}
