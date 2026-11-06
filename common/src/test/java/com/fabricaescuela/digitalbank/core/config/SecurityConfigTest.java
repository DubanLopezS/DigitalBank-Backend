package com.fabricaescuela.digitalbank.core.config;

import com.fabricaescuela.digitalbank.core.security.FiltroAutenticacionJwt;
import com.fabricaescuela.digitalbank.core.web.ApiAccessDeniedHandler;
import com.fabricaescuela.digitalbank.core.web.ApiAuthenticationEntryPoint;
import com.fabricaescuela.digitalbank.core.web.ErrorResponseWriter;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.io.IOException;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Configuración de seguridad compartida")
@SpringJUnitWebConfig(SecurityConfigTest.Contexto.class)
@TestPropertySource(properties = "seguridad.rutas-publicas=/publica")
class SecurityConfigTest {

    private MockMvc mockMvc;

    @BeforeEach
    void prepararMockMvc(WebApplicationContext contexto) {
        Filter cadenaSeguridad = contexto.getBean("springSecurityFilterChain", Filter.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(contexto).addFilters(cadenaSeguridad).build();
    }

    @Test
    @DisplayName("Una ruta pública configurada responde sin token")
    void rutaPublicaSinToken() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/publica"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    @DisplayName("Swagger responde sin token")
    void swaggerSinToken() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Una ruta protegida sin token responde 401 con el formato de error de la API")
    void rutaProtegidaSinToken() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/privada"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.path").value("/privada"));
    }

    @Test
    @DisplayName("Una ruta protegida responde cuando el filtro JWT autentica la petición")
    void rutaProtegidaConToken() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/privada").header("Authorization", "Bearer valido"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Nested
    @DisplayName("Sin rutas públicas propias del servicio")
    @TestPropertySource(properties = "seguridad.rutas-publicas=")
    class SinRutasPublicas {

        @Test
        @DisplayName("Una ruta que no está configurada como pública exige token")
        void rutaNoConfiguradaExigeToken() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/publica"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("NO_AUTENTICADO"));
        }
    }

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, ApiAuthenticationEntryPoint.class,
            ApiAccessDeniedHandler.class, ErrorResponseWriter.class})
    static class Contexto {

        @Bean
        FiltroAutenticacionJwt filtroDePrueba() {
            return new FiltroDePrueba();
        }

        @Bean
        ControladorDePrueba controladorDePrueba() {
            return new ControladorDePrueba();
        }
    }

    /** Autentica solo el token "valido", para no depender del filtro JWT real de cada API. */
    static class FiltroDePrueba implements FiltroAutenticacionJwt {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            String header = ((HttpServletRequest) request).getHeader("Authorization");
            if ("Bearer valido".equals(header)) {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken("usuario", null, List.of()));
            }
            chain.doFilter(request, response);
        }
    }

    @RestController
    static class ControladorDePrueba {

        @GetMapping({"/publica", "/privada", "/v3/api-docs"})
        String responder() {
            return "ok";
        }
    }
}
