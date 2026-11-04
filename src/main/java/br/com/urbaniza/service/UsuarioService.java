package br.com.urbaniza.service;

import br.com.urbaniza.model.*;
import br.com.urbaniza.repository.*;
import br.com.urbaniza.web.Dtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@Transactional
public class UsuarioService {
    private final UsuarioRepository users;
    private final AvaliacaoRepository reviews;
    private final RecuperacaoRepository resets;
    private final PasswordEncoder passwords;
    public UsuarioService(UsuarioRepository users, AvaliacaoRepository reviews, RecuperacaoRepository resets, PasswordEncoder passwords) {
        this.users = users; this.reviews = reviews; this.resets = resets; this.passwords = passwords;
    }
    public static String email(String value) { return value.trim().toLowerCase(Locale.ROOT); }
    public void validatePassword(String value) {
        if (value.getBytes(StandardCharsets.UTF_8).length > 72) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A senha deve ter no máximo 72 bytes UTF-8");
    }
    public Usuario cadastrar(Cadastro data) {
        validatePassword(data.senha());
        if (users.findByUsername(data.username()).isPresent() || users.findByEmail(email(data.email())).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username ou email já cadastrado");
        Usuario u = new Usuario(); u.nome = data.nome().trim(); u.username = data.username();
        u.email = email(data.email()); u.senhaHash = passwords.encode(data.senha());
        return users.saveAndFlush(u);
    }
    public Usuario current(Authentication auth) {
        return users.findById(Long.valueOf(auth.getName())).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida"));
    }
    public Usuario get(Long id) { return users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado")); }
    public Usuario atualizar(Usuario u, Perfil data) {
        users.findByUsername(data.username()).filter(other -> !other.id.equals(u.id)).ifPresent(other -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username já cadastrado");
        });
        u.nome = data.nome().trim(); u.username = data.username(); u.localidade = data.localidade().trim();
        return users.saveAndFlush(u);
    }
    public void excluir(Usuario u) {
        if (u.papel == Usuario.Papel.ADMIN && users.countByPapel(Usuario.Papel.ADMIN) <= 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O último administrador não pode ser excluído");
        reviews.deleteByUsuarioId(u.id); resets.deleteById(u.id); users.delete(u); users.flush();
    }
    public void logout(Usuario u) { u.tokenVersion++; users.save(u); }
    public void checkPassword(Usuario u, String value) {
        validatePassword(value);
        if (!passwords.matches(value, u.senhaHash)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Senha incorreta");
    }
}
