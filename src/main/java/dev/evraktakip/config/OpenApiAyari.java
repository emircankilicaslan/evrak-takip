package dev.evraktakip.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger arayüzü: /swagger. Önce POST /api/auth/giris ile anahtar alın, sonra "Authorize" ile girin. */
@Configuration
public class OpenApiAyari {

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Evrak Takip API")
                        .version("0.1.0")
                        .description("Kurum evrak yönetimi ve 4982 bilgi edinme başvuru takibi: havale zinciri, gizlilik, süre hesabı, "
                                + "arşiv, yetki ve değiştirilemez denetim kaydı. Süre değerleri örnektir."))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
