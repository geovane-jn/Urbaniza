package br.com.urbaniza.service;

import br.com.urbaniza.model.*;
import br.com.urbaniza.repository.*;
import br.com.urbaniza.web.Dtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class AvaliacaoService {
    private final AvaliacaoRepository reviews;
    private final RegiaoRepository regions;
    public AvaliacaoService(AvaliacaoRepository reviews, RegiaoRepository regions) { this.reviews=reviews; this.regions=regions; }
    @Transactional(readOnly = true)
    public List<AvaliacaoView> listar(String regiaoId, Long usuarioId) {
        return reviews.findAll().stream().filter(a -> regiaoId==null || a.regiao.getId().equals(regiaoId))
            .filter(a -> usuarioId==null || a.usuario.getId().equals(usuarioId))
            .sorted(Comparator.comparing((Avaliacao a) -> a.atualizadoEm).reversed()).map(AvaliacaoView::of).toList();
    }
    public AvaliacaoView criar(Usuario u, Avaliar data) {
        if (reviews.existsByUsuarioIdAndRegiaoId(u.id, data.regiaoId())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Você já avaliou esta região. Edite sua avaliação existente.");
        var a=new Avaliacao(); a.usuario=u; fill(a, data); return AvaliacaoView.of(reviews.saveAndFlush(a));
    }
    public AvaliacaoView atualizar(Long id, Usuario u, Avaliar data) {
        var a=find(id); authorize(a,u);
        if (!a.regiao.getId().equals(data.regiaoId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não é possível trocar a região de uma avaliação existente");
        fill(a,data); a.atualizadoEm=Instant.now(); return AvaliacaoView.of(reviews.save(a));
    }
    public void excluir(Long id, Usuario u) { var a=find(id); authorize(a,u); reviews.delete(a); }
    private Avaliacao find(Long id) { return reviews.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Avaliação não encontrada")); }
    private void authorize(Avaliacao a, Usuario u) {
        if (!a.usuario.getId().equals(u.id) && u.papel!=Usuario.Papel.ADMIN) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você só pode alterar suas próprias avaliações");
    }
    private void fill(Avaliacao a, Avaliar data) {
        a.regiao=regions.findById(data.regiaoId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Região não encontrada"));
        a.seguranca=data.seguranca(); a.mobilidade=data.mobilidade(); a.iluminacao=data.iluminacao(); a.comentario=data.comentario().trim();
    }
    private static String normalizar(String value) {
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }
    @Transactional(readOnly = true)
    public List<RegiaoView> regioes(String busca) {
        var grouped=reviews.findAll().stream().collect(Collectors.groupingBy(a -> a.regiao.getId()));
        return regions.findAll().stream().filter(r -> busca==null || normalizar(r.nome).contains(normalizar(busca)))
            .sorted(Comparator.comparing(r -> r.nome)).map(r -> {
                var values=grouped.getOrDefault(r.id,List.of());
                Double s=null,m=null,i=null,g=null;
                if (!values.isEmpty()) {
                    s=values.stream().mapToInt(a -> a.seguranca).average().orElseThrow();
                    m=values.stream().mapToInt(a -> a.mobilidade).average().orElseThrow();
                    i=values.stream().mapToInt(a -> a.iluminacao).average().orElseThrow(); g=(s+m+i)/3;
                }
                return new RegiaoView(r.id,r.nome,r.zona,r.latitude,r.longitude,values.size(),new Notas(g,s,m,i));
            }).toList();
    }
}
