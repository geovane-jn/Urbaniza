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

@Component
public class DadosIniciais implements CommandLineRunner {
    private final RegiaoRepository regions;
    private final UsuarioRepository users;
    private final UsuarioService service;
    @Value("${app.admin.email}") private String email;
    @Value("${app.admin.username}") private String username;
    @Value("${app.admin.password}") private String password;
    public DadosIniciais(RegiaoRepository regions, UsuarioRepository users, UsuarioService service) { this.regions=regions; this.users=users; this.service=service; }
    @Override @Transactional public void run(String... args) {
        List.of(
            new Regiao("vila-mariana","Vila Mariana","Zona Sul • São Paulo",-23.5891,-46.6346),
            new Regiao("pinheiros","Pinheiros","Zona Oeste • São Paulo",-23.5614,-46.6958),
            new Regiao("bela-vista","Bela Vista","Centro • São Paulo",-23.5615,-46.6490),
            new Regiao("se","Sé","Centro • São Paulo",-23.5505,-46.6333),
            new Regiao("moema","Moema","Zona Sul • São Paulo",-23.5975,-46.6658),
            new Regiao("ipiranga","Ipiranga","Zona Sul • São Paulo",-23.5855,-46.6095),
            new Regiao("campo-belo","Campo Belo","Zona Sul • São Paulo",-23.6210,-46.6710),
            new Regiao("saude","Saúde","Zona Sul • São Paulo",-23.6155,-46.6375)
        ).forEach(r -> { if (!regions.existsById(r.id)) regions.save(r); });
        if (!email.isBlank() && !password.isBlank() && users.countByPapel(Usuario.Papel.ADMIN)==0) {
            if (!username.matches("[a-z0-9_]{3,30}") || password.length()<8 || !email.contains("@")) throw new IllegalStateException("Confira ADMIN_USERNAME, ADMIN_EMAIL e ADMIN_PASSWORD");
            // Nunca promova automaticamente uma conta existente apenas por coincidir com um email.
            var u=service.cadastrar(new Cadastro("Administrador",username,email,password));
            u.papel=Usuario.Papel.ADMIN; users.save(u);
        }
    }
}
