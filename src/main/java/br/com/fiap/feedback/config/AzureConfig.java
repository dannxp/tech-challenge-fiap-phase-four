package br.com.fiap.feedback.config;

import br.com.fiap.feedback.application.AdminEmails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Beans de infraestrutura básicos. Os clientes Azure são criados de forma lazy
 * nas classes de infraestrutura para que a aplicação suba mesmo antes de todos
 * os segredos estarem configurados.
 */
@Configuration
public class AzureConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public ZoneId applicationZoneId(FeedbackProperties properties) {
        return ZoneId.of(properties.timezone());
    }

    @Bean
    public AdminEmails adminEmails(FeedbackProperties properties) {
        return new AdminEmails(properties.adminEmails());
    }
}
