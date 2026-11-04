package br.com.urbaniza.web;

import br.com.urbaniza.model.*;
import jakarta.validation.constraints.*;
import java.time.Instant;

public final class Dtos {
    private Dtos() {}
    public record Cadastro(
        @NotBlank @Size(max = 100) String nome,
        @NotBlank @Pattern(regexp = "[a-z0-9_]{3,30}", message = "Use 3 a 30 letras minúsculas, números ou _") String username,
        @NotBlank @jakarta.validation.constraints.Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String senha) {}
    public record Login(@NotBlank @Size(max = 254) String identificador, @NotBlank @Size(max = 72) String senha) {}
    public record Perfil(@NotBlank @Size(max = 100) String nome,
        @NotBlank @Pattern(regexp = "[a-z0-9_]{3,30}") String username,
        @NotBlank @Size(max = 100) String localidade) {}
    public record SenhaAtual(@NotBlank @Size(max = 72) String senha) {}
    public record Email(@NotBlank @jakarta.validation.constraints.Email @Size(max = 254) String email) {}
    public record Codigo(@NotBlank @jakarta.validation.constraints.Email String email,
        @NotBlank @Pattern(regexp = "[0-9]{6}") String codigo) {}
    public record NovaSenha(@NotBlank @Size(max = 100) String token, @NotBlank @Size(min = 8, max = 72) String senha) {}
    public record Papel(@NotNull Usuario.Papel papel) {}
    public record Avaliar(@NotBlank @Size(max = 40) String regiaoId,
        @NotNull @Min(1) @Max(5) Integer seguranca,
        @NotNull @Min(1) @Max(5) Integer mobilidade,
        @NotNull @Min(1) @Max(5) Integer iluminacao,
        @NotNull @Size(max = 1000) String comentario) {}
    public record UsuarioView(Long id, String nome, String username, String email, String localidade, Usuario.Papel papel) {
        public static UsuarioView of(Usuario u) { return new UsuarioView(u.id, u.nome, u.username, u.email, u.localidade, u.papel); }
    }
    public record AvaliacaoView(Long id, Long usuarioId, String username, String nome, String regiaoId, String regiaoNome,
        int seguranca, int mobilidade, int iluminacao, double geral, String comentario, Instant criadoEm, Instant atualizadoEm) {
        public static AvaliacaoView of(Avaliacao a) { return new AvaliacaoView(a.id, a.usuario.getId(), a.usuario.getUsername(), a.usuario.getNome(),
            a.regiao.getId(), a.regiao.getNome(), a.seguranca, a.mobilidade, a.iluminacao,
            (a.seguranca + a.mobilidade + a.iluminacao) / 3.0, a.comentario, a.criadoEm, a.atualizadoEm); }
    }
    public record Notas(Double geral, Double seguranca, Double mobilidade, Double iluminacao) {}
    public record RegiaoView(String id, String nome, String zona, double latitude, double longitude, long totalAvaliacoes, Notas notas) {}
}
