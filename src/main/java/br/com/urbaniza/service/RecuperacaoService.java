package br.com.urbaniza.service;

import br.com.urbaniza.model.RecuperacaoSenha;
import br.com.urbaniza.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

@Service
public class RecuperacaoService {
    private final UsuarioRepository users;
    private final RecuperacaoRepository resets;
    private final PasswordEncoder passwords;
    private final UsuarioService userService;
    private final JavaMailSender mail;
    private final boolean dev;
    private final String from;
    private final SecureRandom random = new SecureRandom();
    public RecuperacaoService(UsuarioRepository users, RecuperacaoRepository resets, PasswordEncoder passwords, UsuarioService userService,
        JavaMailSender mail, @Value("${app.reset.dev-mode}") boolean dev, @Value("${app.mail.from}") String from) {
        this.users=users; this.resets=resets; this.passwords=passwords; this.userService=userService; this.mail=mail; this.dev=dev; this.from=from;
    }
    @Transactional
    public Map<String, String> solicitar(String email) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("message", "Se o email estiver cadastrado, você receberá um código válido por 10 minutos.");
        users.findByEmail(UsuarioService.email(email)).ifPresent(u -> {
            var previous = resets.findById(u.id);
            if (previous.isPresent() && previous.get().enviadoEm.plusSeconds(60).isAfter(Instant.now())) return;
            String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
            RecuperacaoSenha r = new RecuperacaoSenha(); r.usuarioId=u.id; r.codigoHash=passwords.encode(code);
            r.expiraEm=Instant.now().plusSeconds(600); r.enviadoEm=Instant.now(); resets.save(r);
            if (dev) {
                result.put("codigoDesenvolvimento", code);
                result.put("aviso", "Modo local: nenhum email foi enviado. Use este código apenas para testar.");
            } else {
                SimpleMailMessage message = new SimpleMailMessage(); message.setFrom(from); message.setTo(u.email);
                message.setSubject("Urbaniza — recuperação de senha");
                message.setText("Seu código é " + code + ". Válido por 10 minutos. Se não solicitou, ignore esta mensagem.");
                try { mail.send(message); }
                catch (MailException e) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Não foi possível enviar o email. Tente novamente mais tarde."); }
            }
        });
        return result;
    }
    // Tentativas incorretas precisam ser persistidas mesmo quando a resposta é HTTP 400.
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Map<String, String> verificar(String email, String codigo) {
        var user = users.findByEmail(UsuarioService.email(email));
        var found = user.flatMap(u -> resets.findById(u.id));
        if (found.isEmpty()) throw invalid();
        RecuperacaoSenha r = found.get();
        if (r.expiraEm.isBefore(Instant.now()) || r.tentativas >= 5 || r.tokenHash != null) throw invalid();
        r.tentativas++;
        if (!passwords.matches(codigo, r.codigoHash)) { resets.save(r); throw invalid(); }
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        r.tokenHash=hash(token); r.expiraEm=Instant.now().plusSeconds(600); resets.save(r);
        return Map.of("token", token);
    }
    @Transactional
    public void redefinir(String token, String senha) {
        userService.validatePassword(senha);
        var r = resets.findByTokenHash(hash(token)).orElseThrow(this::invalid);
        if (r.expiraEm.isBefore(Instant.now())) throw invalid();
        var u = userService.get(r.usuarioId);
        u.senhaHash=passwords.encode(senha); u.tokenVersion++; users.save(u); resets.delete(r); resets.flush();
    }
    private ResponseStatusException invalid() { return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código ou token inválido, expirado ou já utilizado"); }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
