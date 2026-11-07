package br.com.fiap.feedback.application.port;

import br.com.fiap.feedback.domain.Avaliacao;

import java.time.LocalDate;
import java.util.List;

public interface AvaliacaoRepository {

    void salvar(Avaliacao avaliacao);

    List<Avaliacao> buscarPorPeriodo(LocalDate inicio, LocalDate fim);
}
