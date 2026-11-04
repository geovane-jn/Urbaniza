package br.com.urbaniza.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "avaliacoes", uniqueConstraints = @UniqueConstraint(columnNames = {"usuario_id", "regiao_id"}))
public class Avaliacao {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "usuario_id")
    public Usuario usuario;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "regiao_id")
    public Regiao regiao;
    public int seguranca;
    public int mobilidade;
    public int iluminacao;
    @Column(nullable = false, length = 1000)
    public String comentario;
    public Instant criadoEm = Instant.now();
    public Instant atualizadoEm = Instant.now();
}
