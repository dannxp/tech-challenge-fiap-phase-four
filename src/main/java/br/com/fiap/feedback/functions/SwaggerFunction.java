package br.com.fiap.feedback.functions;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.functions.HttpMethod;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Component
public class SwaggerFunction {

    private static final String OPENAPI_RESOURCE = "/openapi.yaml";
    private static final String SWAGGER_UI_HTML = """
            <!doctype html>
            <html lang="pt-BR">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Feedback de aulas - Swagger UI</title>
              <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css">
            </head>
            <body>
              <div id="swagger-ui"></div>
              <script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
              <script>
                window.onload = () => SwaggerUIBundle({
                  url: "./openapi.yaml",
                  dom_id: "#swagger-ui",
                  deepLinking: true,
                  presets: [SwaggerUIBundle.presets.apis],
                  layout: "BaseLayout"
                });
              </script>
            </body>
            </html>
            """;

    @FunctionName("swagger")
    public HttpResponseMessage swagger(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.GET},
                    authLevel = AuthorizationLevel.ANONYMOUS,
                    route = "swagger")
            HttpRequestMessage<Optional<String>> request,
            ExecutionContext context) {
        return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(SWAGGER_UI_HTML)
                .build();
    }

    @FunctionName("openapi")
    public HttpResponseMessage openapi(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.GET},
                    authLevel = AuthorizationLevel.ANONYMOUS,
                    route = "openapi.yaml")
            HttpRequestMessage<Optional<String>> request,
            ExecutionContext context) {
        try (InputStream input = SwaggerFunction.class.getResourceAsStream(OPENAPI_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Recurso OpenAPI não encontrado: " + OPENAPI_RESOURCE);
            }

            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/yaml; charset=UTF-8")
                    .body(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                    .build();
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível carregar o contrato OpenAPI.", e);
        }
    }
}
