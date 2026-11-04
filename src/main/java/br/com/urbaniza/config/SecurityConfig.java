package br.com.urbaniza.config;

import br.com.urbaniza.repository.UsuarioRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.*;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean SecretKey jwtKey(@Value("${app.jwt.secret}") String configured, Environment env) throws Exception {
        byte[] bytes;
        if (!configured.isBlank()) {
            bytes = Base64.getDecoder().decode(configured);
        } else {
            if (!env.matchesProfiles("dev", "test")) throw new IllegalStateException("Configure JWT_SECRET (base64, ao menos 32 bytes) fora de dev/test");
            Path path = Path.of(".urbaniza/jwt.key");
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) {
                byte[] random = new byte[32]; new SecureRandom().nextBytes(random);
                if (Files.getFileStore(path.getParent()).supportsFileAttributeView("posix")) {
                    Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
                } else {
                    // Windows usa as ACLs herdadas da pasta do usuário, sem atributos POSIX.
                    Files.createFile(path);
                }
                Files.writeString(path, Base64.getEncoder().encodeToString(random));
            }
            bytes = Base64.getDecoder().decode(Files.readString(path).trim());
        }
        if (bytes.length < 32) throw new IllegalStateException("JWT_SECRET precisa de pelo menos 32 bytes");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
    @Bean JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }
    @Bean JwtDecoder jwtDecoder(SecretKey key) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("urbaniza"));
        return decoder;
    }
    @Bean SecurityFilterChain security(HttpSecurity http, UsuarioRepository users) throws Exception {
        http.csrf(csrf -> csrf.disable()) // API sem cookies de autenticação: o frontend envia Bearer JWT.
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/cadastro", "/api/auth/login", "/api/auth/recuperar", "/api/auth/verificar", "/api/auth/nova-senha").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/", "/*.html", "/*.css", "/*.js", "/assets/**").permitAll()
                .requestMatchers("/error").permitAll()
                .anyRequest().denyAll())
            .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(jwt -> {
                try {
                    var u = users.findById(Long.parseLong(jwt.getSubject())).orElseThrow();
                    Number version = jwt.getClaim("version");
                    if (version == null || version.longValue() != u.tokenVersion) throw new IllegalArgumentException();
                    return (AbstractAuthenticationToken) new JwtAuthenticationToken(jwt,
                        List.of(new SimpleGrantedAuthority("ROLE_" + u.papel.name())), u.id.toString());
                } catch (RuntimeException e) {
                    throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"), "Sessão inválida; entre novamente");
                }
            })))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> { res.setStatus(401); res.setContentType("application/json;charset=UTF-8"); res.getWriter().write("{\"message\":\"Entre novamente para continuar\"}"); })
                .accessDeniedHandler((req, res, ex) -> { res.setStatus(403); res.setContentType("application/json;charset=UTF-8"); res.getWriter().write("{\"message\":\"Você não tem permissão para esta operação\"}"); }));
        return http.build();
    }
}
