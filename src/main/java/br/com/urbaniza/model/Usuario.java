package br.com.urbaniza.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "usuarios")
public class Usuario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false, unique = true, length = 30)
    public String username;
    @Column(nullable = false, length = 100)
    public String nome;
    @Column(nullable = false, unique = true, length = 254)
    public String email;
    @Column(nullable = false)
    public String senhaHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    public Papel papel = Papel.USER;
    @Column(nullable = false, length = 100)
    public String localidade = "São Paulo • SP";
    public long tokenVersion = 0;
    public Instant criadoEm = Instant.now();
    public enum Papel { USER, ADMIN }
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getNome() { return nome; }
    public String getEmail() { return email; }
    public Papel getPapel() { return papel; }
}
