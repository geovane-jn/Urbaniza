package br.com.urbaniza.repository;
import br.com.urbaniza.model.Avaliacao;
import org.springframework.data.jpa.repository.*;
import java.util.List;
public interface AvaliacaoRepository extends JpaRepository<Avaliacao, Long> {
    @Override @EntityGraph(attributePaths = {"usuario", "regiao"})
    List<Avaliacao> findAll();
    boolean existsByUsuarioIdAndRegiaoId(Long usuarioId, String regiaoId);
    void deleteByUsuarioId(Long usuarioId);
}
