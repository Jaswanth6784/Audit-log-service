package com.jaswanth.auditlog.shared.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityProblemWriter problemWriter;
    private final String challenge;

    public JsonAuthenticationEntryPoint(SecurityProblemWriter problemWriter, String challenge) {
        this.problemWriter = problemWriter;
        this.challenge = challenge;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException) throws IOException, ServletException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, challenge);
        problemWriter.write(
                response,
                HttpStatus.UNAUTHORIZED,
                "Authentication required",
                "Valid credentials are required to access this resource");
    }
}
