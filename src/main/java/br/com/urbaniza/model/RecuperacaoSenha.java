package br.com.urbaniza.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "recuperacoes_senha")
public class RecuperacaoSenha {
    @Id
    public Long usuarioId;
    @Column(nullable = false)
    public String codigoHash;
    public String tokenHash;
    public Instant expiraEm;
    public Instant enviadoEm;
    public int tentativas;
}
