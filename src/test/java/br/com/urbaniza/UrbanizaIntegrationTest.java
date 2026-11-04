package br.com.urbaniza;

import br.com.urbaniza.model.Usuario;
import br.com.urbaniza.repository.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.time.Instant;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UrbanizaIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository users;
    @Autowired AvaliacaoRepository reviews;
    @Autowired RecuperacaoRepository resets;
    @Autowired JwtEncoder encoder;
    @BeforeEach void clear() { reviews.deleteAll(); resets.deleteAll(); users.deleteAll(); }
    String body(Object value) throws Exception { return json.writeValueAsString(value); }
    JsonNode read(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
    MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request,String token) { return request.header("Authorization","Bearer "+token); }
    JsonNode register(String username) throws Exception {
        return read(mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("nome","Pessoa "+username,"username",username,"email",username+"@test.com","senha","Senha123!"))))
            .andExpect(status().isCreated()).andReturn());
    }
    String token(String name) throws Exception { return register(name).path("token").asText(); }
    Map<String,Object> review(String region,int score) { return Map.of("regiaoId",region,"seguranca",score,"mobilidade",score,"iluminacao",score,"comentario","Boa região"); }
    JsonNode create(String token,String region,int score) throws Exception {
        return read(mvc.perform(auth(post("/api/avaliacoes"),token).contentType(MediaType.APPLICATION_JSON).content(body(review(region,score))))
            .andExpect(status().isCreated()).andReturn());
    }
    String admin() throws Exception { String token=token("admin"); var u=users.findByUsername("admin").orElseThrow(); u.papel=Usuario.Papel.ADMIN; users.save(u); return token; }

    @Test void cadastroLoginUsernameEmailEHash() throws Exception {
        var session=register("ana"); assertThat(session.path("usuario").has("senhaHash")).isFalse();
        var stored=users.findByUsername("ana").orElseThrow(); assertThat(stored.senhaHash).startsWith("$2").isNotEqualTo("Senha123!");
        for(String key:new String[]{"ana","ANA@TEST.COM"}) mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("identificador",key,"senha","Senha123!")))).andExpect(status().isOk()).andExpect(jsonPath("$.token").isString());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body(Map.of("identificador","ana","senha","errada")))).andExpect(status().isUnauthorized());
    }
    @Test void validacaoDuplicidadeEMassAssignment() throws Exception {
        register("ana");
        mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content(body(Map.of("nome","Ana","username","ana","email","ana@test.com","senha","Senha123!")))).andExpect(status().isConflict());
        mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content(body(Map.of("nome"," ","username","x","email","invalido","senha","curta")))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields").exists());
        mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content(body(Map.of("nome","Ana","username","outra","email","outra@test.com","senha","Senha123!","papel","ADMIN")))).andExpect(status().isBadRequest());
    }
    @Test void jwtObrigatorioInvalidoExpiradoETamper() throws Exception {
        mvc.perform(get("/api/regioes")).andExpect(status().isUnauthorized());
        mvc.perform(auth(get("/api/regioes"),"invalido")).andExpect(status().isUnauthorized());
        var session=register("ana"); String token=session.path("token").asText();
        String[] pieces=token.split("\\."); pieces[2]=(pieces[2].startsWith("A")?"B":"A")+pieces[2].substring(1);
        mvc.perform(auth(get("/api/regioes"),String.join(".",pieces))).andExpect(status().isUnauthorized());
        Instant past=Instant.now().minusSeconds(600);
        var claims=JwtClaimsSet.builder().issuer("urbaniza").subject(session.path("usuario").path("id").asText()).issuedAt(past).expiresAt(past.plusSeconds(60)).claim("version",0).build();
        var expired=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
        mvc.perform(auth(get("/api/regioes"),expired)).andExpect(status().isUnauthorized());
    }
    @Test void perfilUsernamePutPrivacidadeEDuplicidade() throws Exception {
        String ana=token("ana"),bia=token("bia");
        mvc.perform(auth(get("/api/usuarios/ana"),ana)).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("ana"));
        mvc.perform(auth(get("/api/usuarios/ana"),bia)).andExpect(status().isForbidden());
        mvc.perform(auth(put("/api/usuarios/me"),ana).contentType(MediaType.APPLICATION_JSON).content(body(Map.of("nome","Ana Maria","username","ana_maria","localidade","São Paulo"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.nome").value("Ana Maria"));
        mvc.perform(auth(get("/api/usuarios/ana_maria"),ana)).andExpect(status().isOk());
        mvc.perform(auth(put("/api/usuarios/me"),ana).contentType(MediaType.APPLICATION_JSON).content(body(Map.of("nome","Ana","username","bia","localidade","SP")))).andExpect(status().isConflict());
    }
    @Test void crudAvaliacoesEMediasReais() throws Exception {
        String ana=token("ana"),bia=token("bia");
        mvc.perform(auth(get("/api/regioes?busca=Pinheiros"),ana)).andExpect(jsonPath("$[0].totalAvaliacoes").value(0)).andExpect(jsonPath("$[0].notas.geral").isEmpty());
        var a=create(ana,"pinheiros",5); create(bia,"pinheiros",3);
        mvc.perform(auth(get("/api/regioes?busca=Pinheiros"),ana)).andExpect(jsonPath("$[0].totalAvaliacoes").value(2)).andExpect(jsonPath("$[0].notas.geral").value(4.0));
        mvc.perform(auth(put("/api/avaliacoes/"+a.path("id").asLong()),ana).contentType(MediaType.APPLICATION_JSON).content(body(review("pinheiros",1)))).andExpect(status().isOk());
        mvc.perform(auth(get("/api/regioes?busca=Pinheiros"),ana)).andExpect(jsonPath("$[0].notas.geral").value(2.0));
        mvc.perform(auth(get("/api/avaliacoes?minhas=true"),ana)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(auth(delete("/api/avaliacoes/"+a.path("id").asLong()),ana)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/regioes?busca=Pinheiros"),ana)).andExpect(jsonPath("$[0].notas.geral").value(3.0));
    }
    @Test void avaliacoesOwnershipValidacaoDuplicidadeERegiaoInexistente() throws Exception {
        String ana=token("ana"),bia=token("bia"); var review=create(ana,"se",4); long id=review.path("id").asLong();
        mvc.perform(auth(put("/api/avaliacoes/"+id),bia).contentType(MediaType.APPLICATION_JSON).content(body(review("se",2)))).andExpect(status().isForbidden());
        mvc.perform(auth(delete("/api/avaliacoes/"+id),bia)).andExpect(status().isForbidden());
        mvc.perform(auth(post("/api/avaliacoes"),ana).contentType(MediaType.APPLICATION_JSON).content(body(review("se",4)))).andExpect(status().isConflict());
        mvc.perform(auth(post("/api/avaliacoes"),ana).contentType(MediaType.APPLICATION_JSON).content(body(review("moema",6)))).andExpect(status().isBadRequest());
        mvc.perform(auth(post("/api/avaliacoes"),ana).contentType(MediaType.APPLICATION_JSON).content(body(review("ausente",3)))).andExpect(status().isNotFound());
        mvc.perform(auth(put("/api/avaliacoes/"+id),ana).contentType(MediaType.APPLICATION_JSON).content(body(review("moema",3)))).andExpect(status().isBadRequest());
    }
    @Test void rbacAdminListagemConsultaModeracaoETrocaDePapel() throws Exception {
        String ana=token("ana"),admin=admin(); var review=create(ana,"moema",4); long userId=users.findByUsername("ana").orElseThrow().id;
        mvc.perform(auth(get("/api/admin/usuarios"),ana)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/admin/usuarios"),admin)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(auth(get("/api/usuarios/ana"),admin)).andExpect(status().isOk());
        mvc.perform(auth(delete("/api/avaliacoes/"+review.path("id").asLong()),admin)).andExpect(status().isNoContent());
        mvc.perform(auth(put("/api/admin/usuarios/"+userId+"/papel"),admin).contentType(MediaType.APPLICATION_JSON).content("{\"papel\":\"ADMIN\"}")).andExpect(status().isOk());
        mvc.perform(auth(get("/api/usuarios/me"),ana)).andExpect(status().isUnauthorized());
        String renewed=read(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"identificador\":\"ana\",\"senha\":\"Senha123!\"}")).andExpect(status().isOk()).andReturn()).path("token").asText();
        mvc.perform(auth(get("/api/admin/usuarios"),renewed)).andExpect(status().isOk());
    }
    @Test void protegeUltimoAdmin() throws Exception {
        String token=admin(); long id=users.findByUsername("admin").orElseThrow().id;
        mvc.perform(auth(delete("/api/admin/usuarios/"+id),token)).andExpect(status().isConflict());
        mvc.perform(auth(put("/api/admin/usuarios/"+id+"/papel"),token).contentType(MediaType.APPLICATION_JSON).content("{\"papel\":\"USER\"}")).andExpect(status().isConflict());
    }
    @Test void logoutRevogaToken() throws Exception {
        String token=token("ana"); mvc.perform(auth(post("/api/auth/logout"),token)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/usuarios/me"),token)).andExpect(status().isUnauthorized());
    }
    @Test void exclusaoContaCascataESenhaObrigatoria() throws Exception {
        String token=token("ana"); create(token,"se",4);
        mvc.perform(auth(delete("/api/usuarios/me"),token).contentType(MediaType.APPLICATION_JSON).content("{\"senha\":\"errada\"}")).andExpect(status().isUnauthorized());
        mvc.perform(auth(delete("/api/usuarios/me"),token).contentType(MediaType.APPLICATION_JSON).content("{\"senha\":\"Senha123!\"}")).andExpect(status().isNoContent());
        assertThat(users.count()).isZero(); assertThat(reviews.count()).isZero();
        mvc.perform(auth(get("/api/usuarios/me"),token)).andExpect(status().isUnauthorized());
    }
    JsonNode requestCode() throws Exception { return read(mvc.perform(post("/api/auth/recuperar").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"ana@test.com\"}")).andExpect(status().isOk()).andReturn()); }
    @Test void recuperacaoCompletaUsoUnicoERevogacao() throws Exception {
        String old=token("ana"); String code=requestCode().path("codigoDesenvolvimento").asText();
        assertThat(resets.findAll().getFirst().codigoHash).isNotEqualTo(code);
        String resetToken=read(mvc.perform(post("/api/auth/verificar").contentType(MediaType.APPLICATION_JSON).content(body(Map.of("email","ana@test.com","codigo",code)))).andExpect(status().isOk()).andReturn()).path("token").asText();
        mvc.perform(post("/api/auth/verificar").contentType(MediaType.APPLICATION_JSON).content(body(Map.of("email","ana@test.com","codigo",code)))).andExpect(status().isBadRequest());
        String data=body(Map.of("token",resetToken,"senha","NovaSenha123!"));
        mvc.perform(post("/api/auth/nova-senha").contentType(MediaType.APPLICATION_JSON).content(data)).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/nova-senha").contentType(MediaType.APPLICATION_JSON).content(data)).andExpect(status().isBadRequest());
        mvc.perform(auth(get("/api/usuarios/me"),old)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"identificador\":\"ana\",\"senha\":\"NovaSenha123!\"}")).andExpect(status().isOk());
    }
    @Test void recuperacaoLimiteTentativasCooldownEExpiracao() throws Exception {
        register("ana"); String code=requestCode().path("codigoDesenvolvimento").asText();
        assertThat(requestCode().has("codigoDesenvolvimento")).isFalse();
        String wrong=code.equals("000000")?"111111":"000000";
        for(int i=0;i<5;i++) mvc.perform(post("/api/auth/verificar").contentType(MediaType.APPLICATION_JSON).content(body(Map.of("email","ana@test.com","codigo",wrong)))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/verificar").contentType(MediaType.APPLICATION_JSON).content(body(Map.of("email","ana@test.com","codigo",code)))).andExpect(status().isBadRequest());
        var r=resets.findAll().getFirst(); assertThat(r.tentativas).isEqualTo(5); r.tentativas=0; r.expiraEm=Instant.now().minusSeconds(1); resets.save(r);
        mvc.perform(post("/api/auth/verificar").contentType(MediaType.APPLICATION_JSON).content(body(Map.of("email","ana@test.com","codigo",code)))).andExpect(status().isBadRequest());
    }
    @Test void emailDesconhecidoNaoEnviaCodigo() throws Exception {
        mvc.perform(post("/api/auth/recuperar").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"ausente@test.com\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.codigoDesenvolvimento").doesNotExist());
    }
    @Test void buscaDeRegiaoIgnoraAcentos() throws Exception {
        String token=token("ana");
        mvc.perform(auth(get("/api/regioes?busca=saude"),token)).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value("saude"));
    }
    @Test void paginasRecursosEEntradaServidos() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/Index.html"));
        for(String p:new String[]{"Index.html","Cadastro.html","inicio.html","perfil.html","mapa.html","avaliacoes.html","NovaSenha.html","admin.html","App.js","assets/leaflet/leaflet.js","assets/distritos-sp.geojson"}) mvc.perform(get("/"+p)).andExpect(status().isOk());
    }
}
