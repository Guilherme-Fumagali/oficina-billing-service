package com.oficina.billing.infrastructure.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oficina.billing.infrastructure.security.filter.JwtAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final ObjectMapper mapper;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter, ObjectMapper mapper) {
        this.jwtFilter = jwtFilter;
        this.mapper = mapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((requisicao, resposta, erro) ->
                    responder(resposta, HttpStatus.UNAUTHORIZED, "Autenticação necessária."))
                .accessDeniedHandler((requisicao, resposta, erro) ->
                    responder(resposta, HttpStatus.FORBIDDEN, "Acesso negado para o papel do solicitante.")))
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/orcamentos/*/decisao").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/orcamentos/*/decisao").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/pagamentos/webhooks/mercado-pago").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/pagamentos/simulacao/*").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/api/**").hasAnyRole("CLIENTE", "FUNCIONARIO")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    private void responder(HttpServletResponse resposta, HttpStatus status, String detalhe) throws IOException {
        resposta.setStatus(status.value());
        resposta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        resposta.setCharacterEncoding("UTF-8");
        mapper.writeValue(resposta.getOutputStream(), ProblemDetail.forStatusAndDetail(status, detalhe));
    }
}
