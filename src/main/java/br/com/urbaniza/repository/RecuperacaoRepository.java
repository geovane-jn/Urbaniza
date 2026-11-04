package br.com.urbaniza.repository;
import br.com.urbaniza.model.RecuperacaoSenha;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface RecuperacaoRepository extends JpaRepository<RecuperacaoSenha, Long> {
    @Override @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RecuperacaoSenha> findById(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RecuperacaoSenha> findByTokenHash(String tokenHash);
}
