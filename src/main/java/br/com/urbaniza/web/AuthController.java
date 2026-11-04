package br.com.urbaniza.web;

import br.com.urbaniza.service.*;
import br.com.urbaniza.web.Dtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final UsuarioService users;
    private final RecuperacaoService resets;
    public AuthController(AuthService auth, UsuarioService users, RecuperacaoService resets) { this.auth=auth; this.users=users; this.resets=resets; }
    @PostMapping("/cadastro") @ResponseStatus(HttpStatus.CREATED)
    public AuthService.Session cadastrar(@Valid @RequestBody Cadastro data) { return auth.session(users.cadastrar(data)); }
    @PostMapping("/login") public AuthService.Session login(@Valid @RequestBody Login data) { return auth.login(data); }
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(Authentication a) { users.logout(users.current(a)); }
    @PostMapping("/recuperar") public Map<String, String> recuperar(@Valid @RequestBody Email data) { return resets.solicitar(data.email()); }
    @PostMapping("/verificar") public Map<String, String> verificar(@Valid @RequestBody Codigo data) { return resets.verificar(data.email(), data.codigo()); }
    @PostMapping("/nova-senha") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void novaSenha(@Valid @RequestBody NovaSenha data) { resets.redefinir(data.token(), data.senha()); }
}
