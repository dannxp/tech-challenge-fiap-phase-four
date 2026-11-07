package br.com.fiap.feedback;

import br.com.fiap.feedback.config.FeedbackProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Ponto de entrada do Spring. O adapter do Spring Cloud Function para Azure
 * localiza esta classe (ou a variável MAIN_CLASS) e inicializa o contexto sob
 * demanda na primeira execução de uma função.
 */
@SpringBootApplication
@EnableConfigurationProperties(FeedbackProperties.class)
public class FeedbackApplication {

    public static void main(String[] args) {
        SpringApplication.run(FeedbackApplication.class, args);
    }
}
