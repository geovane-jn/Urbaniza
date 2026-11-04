package br.com.urbaniza.service;

import br.com.urbaniza.model.Usuario;
import br.com.urbaniza.repository.UsuarioRepository;
import br.com.urbaniza.web.Dtos.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {
    private final UsuarioRepository users;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final long ttl;
    private final String dummyHash;
    public AuthService(UsuarioRepository users, PasswordEncoder passwords, JwtEncoder encoder, @Value("${app.jwt.ttl-seconds}") long ttl) {
        this.users = users; this.passwords = passwords; this.encoder = encoder; this.ttl = ttl;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }
    public record Session(String token, Instant expiraEm, UsuarioView usuario) {}
    public Session login(Login data) {
        if (data.senha().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email/username ou senha incorretos");
        String key = UsuarioService.email(data.identificador());
        var found = key.contains("@") ? users.findByEmail(key) : users.findByUsername(key);
        boolean valid = passwords.matches(data.senha(), found.map(u -> u.senhaHash).orElse(dummyHash));
        if (!valid || found.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email/username ou senha incorretos");
        return session(found.get());
    }
    public Session session(Usuario u) {
        Instant now = Instant.now(), expires = now.plusSeconds(ttl);
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("urbaniza").subject(u.id.toString())
            .issuedAt(now).expiresAt(expires).id(UUID.randomUUID().toString()).claim("version", u.tokenVersion).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new Session(token, expires, UsuarioView.of(u));
    }
}
