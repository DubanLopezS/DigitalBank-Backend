package com.fabricaescuela.digitalbank.core.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

@Component
public class ErrorResponseWriter {

    public void write(HttpServletRequest request,
                        HttpServletResponse response,
                        HttpStatus status,
                        String errorCode,
                        String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");

        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        String body = "{"
                + "\"timestamp\":\"" + Instant.now() + "\","
                + "\"status\":" + status.value() + ","
                + "\"error\":\"" + escapar(status.getReasonPhrase()) + "\","
                + "\"errorCode\":\"" + escapar(errorCode) + "\","
                + "\"message\":\"" + escapar(message) + "\","
                + "\"path\":\"" + escapar(request.getRequestURI()) + "\","
                + "\"traceId\":" + (traceId == null ? "null" : "\"" + escapar(traceId) + "\"")
                + "}";
        response.getWriter().write(body);
    }

    private String escapar(String texto) {
        StringBuilder resultado = new StringBuilder();
        for (char c : texto.toCharArray()) {
            switch (c) {
                case '"' -> resultado.append("\\\"");
                case '\\' -> resultado.append("\\\\");
                case '\n' -> resultado.append("\\n");
                case '\r' -> resultado.append("\\r");
                case '\t' -> resultado.append("\\t");
                default -> resultado.append(c < 0x20 ? " " : String.valueOf(c));
            }
        }
        return resultado.toString();
    }
}