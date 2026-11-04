package br.com.urbaniza.model;

import jakarta.persistence.*;

@Entity
@Table(name = "regioes")
public class Regiao {
    @Id @Column(length = 40)
    public String id;
    @Column(nullable = false, length = 100)
    public String nome;
    @Column(nullable = false, length = 100)
    public String zona;
    public double latitude;
    public double longitude;
    public Regiao() {}
    public Regiao(String id, String nome, String zona, double latitude, double longitude) {
        this.id = id; this.nome = nome; this.zona = zona;
        this.latitude = latitude; this.longitude = longitude;
    }
    public String getId() { return id; }
    public String getNome() { return nome; }
}
