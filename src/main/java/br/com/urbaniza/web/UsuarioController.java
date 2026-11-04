package br.com.urbaniza.web;

import br.com.urbaniza.repository.UsuarioRepository;
import br.com.urbaniza.service.UsuarioService;
import br.com.urbaniza.model.Usuario;
import br.com.urbaniza.web.Dtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api")
public class UsuarioController {
    private final UsuarioService service;
    private final UsuarioRepository users;
    public UsuarioController(UsuarioService service, UsuarioRepository users) { this.service=service; this.users=users; }
    @GetMapping("/usuarios/me") public UsuarioView me(Authentication a) { return UsuarioView.of(service.current(a)); }
    @GetMapping("/usuarios/{username}") public UsuarioView username(@PathVariable String username, Authentication a) {
        var current=service.current(a);
        if (!current.username.equals(username) && current.papel != Usuario.Papel.ADMIN) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso restrito ao próprio perfil ou administrador");
        return UsuarioView.of(users.findByUsername(username).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado")));
    }
    @PutMapping("/usuarios/me") public UsuarioView update(Authentication a, @Valid @RequestBody Perfil data) { return UsuarioView.of(service.atualizar(service.current(a), data)); }
    @DeleteMapping("/usuarios/me") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication a, @Valid @RequestBody SenhaAtual data) { var u=service.current(a); service.checkPassword(u, data.senha()); service.excluir(u); }
    @GetMapping("/admin/usuarios") public List<UsuarioView> list() { return users.findAll().stream().map(UsuarioView::of).toList(); }
    @DeleteMapping("/admin/usuarios/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAdmin(@PathVariable Long id) { service.excluir(service.get(id)); }
    @PutMapping("/admin/usuarios/{id}/papel") @Transactional
    public UsuarioView role(@PathVariable Long id, @Valid @RequestBody Papel data) {
        var u=service.get(id);
        if (u.papel == Usuario.Papel.ADMIN && data.papel() == Usuario.Papel.USER && users.countByPapel(Usuario.Papel.ADMIN) <= 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O último administrador não pode perder seu papel");
        u.papel=data.papel(); u.tokenVersion++; return UsuarioView.of(users.save(u));
    }
}
