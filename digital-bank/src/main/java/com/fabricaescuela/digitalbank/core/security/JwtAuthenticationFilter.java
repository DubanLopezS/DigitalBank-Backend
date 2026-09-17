package com.fabricaescuela.digitalbank.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Se ejecuta en cada request. Si viene un JWT válido en el header Authorization,
 * le informa a Spring Security quién es el usuario y qué rol tiene,
 * para que @PreAuthorize pueda evaluar permisos más adelante.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            if (jwtService.esTokenValido(token)) {
                UUID usuarioId = jwtService.extraerUsuarioId(token);
                String rol = jwtService.extraerRol(token);

                var authentication = new UsernamePasswordAuthenticationToken(
                        usuarioId,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + rol))
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}