package br.com.urbaniza.config;

import br.com.urbaniza.model.*;
import br.com.urbaniza.repository.*;
import br.com.urbaniza.service.UsuarioService;
import br.com.urbaniza.web.Dtos.Cadastro;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.io.IOException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

@Component
public class DadosIniciais implements CommandLineRunner {
    private final RegiaoRepository regions;
    private final UsuarioRepository users;
    private final UsuarioService service;
    private final ObjectMapper json;
    @Value("${app.admin.email}") private String email;
    @Value("${app.admin.username}") private String username;
    @Value("${app.admin.password}") private String password;
    public DadosIniciais(RegiaoRepository regions, UsuarioRepository users, UsuarioService service, ObjectMapper json) { this.regions=regions; this.users=users; this.service=service; this.json=json; }
    @Override @Transactional public void run(String... args) throws IOException {
        try (var input = new ClassPathResource("regioes-sp.json").getInputStream()) {
            List<Regiao> catalogo = json.readValue(input, new TypeReference<>() {});
            // Adiciona os bairros e distritos ausentes sem apagar ou recriar avaliações existentes.
            catalogo.forEach(r -> {
                var existente = regions.findById(r.id);
                if (existente.isEmpty()) regions.save(r);
                else {
                    var atual = existente.get();
                    // Completa a classificação sem alterar IDs, coordenadas ou avaliações.
                    if (atual.tipo == null) atual.tipo = r.tipo;
                    if (atual.distritoId == null) atual.distritoId = r.distritoId;
                }
            });
        }
        if (!email.isBlank() && !password.isBlank() && users.countByPapel(Usuario.Papel.ADMIN)==0) {
            if (!username.matches("[a-z0-9_]{3,30}") || password.length()<8 || !email.contains("@")) throw new IllegalStateException("Confira ADMIN_USERNAME, ADMIN_EMAIL e ADMIN_PASSWORD");
            // Nunca promova automaticamente uma conta existente apenas por coincidir com um email.
            var u=service.cadastrar(new Cadastro("Administrador",username,email,password));
            u.papel=Usuario.Papel.ADMIN; users.save(u);
        }
    }
}
