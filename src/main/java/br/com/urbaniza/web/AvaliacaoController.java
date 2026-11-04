package br.com.urbaniza.web;

import br.com.urbaniza.service.*;
import br.com.urbaniza.web.Dtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class AvaliacaoController {
    private final AvaliacaoService service;
    private final UsuarioService users;
    public AvaliacaoController(AvaliacaoService service, UsuarioService users) { this.service=service; this.users=users; }
    @GetMapping("/regioes") public List<RegiaoView> regions(@RequestParam(required=false) String busca) { return service.regioes(busca); }
    @GetMapping("/avaliacoes") public List<AvaliacaoView> list(@RequestParam(required=false) String regiaoId,
        @RequestParam(defaultValue="false") boolean minhas, Authentication auth) {
        return service.listar(regiaoId,minhas ? users.current(auth).id : null);
    }
    @PostMapping("/avaliacoes") @ResponseStatus(HttpStatus.CREATED)
    public AvaliacaoView create(@Valid @RequestBody Avaliar data, Authentication auth) { return service.criar(users.current(auth),data); }
    @PutMapping("/avaliacoes/{id}") public AvaliacaoView update(@PathVariable Long id, @Valid @RequestBody Avaliar data, Authentication auth) { return service.atualizar(id,users.current(auth),data); }
    @DeleteMapping("/avaliacoes/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication auth) { service.excluir(id,users.current(auth)); }
}
