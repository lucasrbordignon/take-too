package com.kinkan.take_too.security;

import com.kinkan.take_too.domain.dto.VersaoCreateDTO;
import com.kinkan.take_too.domain.entity.*;
import com.kinkan.take_too.domain.enums.EtapaProjeto;
import com.kinkan.take_too.domain.enums.StatusImagem;
import com.kinkan.take_too.repository.*;
import com.kinkan.take_too.service.ImagemService;
import com.kinkan.take_too.service.VersaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"app.imagem.enabled=false", "app.imagem.provider=fake"})
@Import(ImagemIntegrationTest.Config.class)
@EnabledIfSystemProperty(named = "taketoo.postgres", matches = "true")
class ImagemConcorrenciaPostgresTest {
    @Autowired private ProfissionalRepository profissionais;
    @Autowired private ClienteRepository clientes;
    @Autowired private ProjetoRepository projetos;
    @Autowired private ImagemAssetRepository imagens;
    @Autowired private VersaoRepository versoes;
    @Autowired private ImagemService imagemService;
    @Autowired private VersaoService versaoService;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private com.kinkan.take_too.config.ImagemProperties properties;
    private UUID profissionalId;
    private UUID projetoId;
    private UUID imagemId;

    @BeforeEach
    void preparar() {
        properties.setEnabled(true);
        Profissional profissional = new Profissional();
        profissional.setNome("Concorrência");
        profissional.setEmail(UUID.randomUUID() + "@example.com");
        profissional.setSenhaHash("test");
        profissional.setPlano("PRO");
        profissional = profissionais.save(profissional);
        profissionalId = profissional.getId();
        Cliente cliente = new Cliente();
        cliente.setNome("Cliente");
        cliente.setTelefone(UUID.randomUUID().toString());
        cliente = clientes.save(cliente);
        Projeto projeto = new Projeto();
        projeto.setProfissional(profissional);
        projeto.setCliente(cliente);
        projeto.setNome("Concorrência");
        projeto.setEtapaAtual(EtapaProjeto.EM_EDICAO);
        projetoId = projetos.save(projeto).getId();
        ImagemAsset imagem = new ImagemAsset();
        imagem.setId(UUID.randomUUID());
        imagem.setProfissional(profissional);
        imagem.setNome("Pronto");
        imagem.setProvedor("fake");
        imagem.setStatus(StatusImagem.PRONTA);
        imagem.setChave("imagens/" + imagem.getId());
        imagem.setTipo("image/png");
        imagem.setChecksum("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        imagem.setTamanhoBytes(100);
        imagemId = imagens.save(imagem).getId();
    }

    @Test
    void deveAguardarPublicacaoAntesDeRejeitarExclusaoConcorrente() throws Exception {
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?>[] pending = new Future<?>[1];
            new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                versaoService.criarVersao(profissionalId, projetoId, new VersaoCreateDTO(null, null, imagemId));
                pending[0] = executor.submit(() -> imagemService.excluir(profissionalId, imagemId));
                assertThrows(TimeoutException.class, () -> pending[0].get(250, TimeUnit.MILLISECONDS));
            });
            ExecutionException error = assertThrows(ExecutionException.class, () -> pending[0].get(10, TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class, error.getCause());
            assertTrue(versoes.existsByImagem_Id(imagemId));
            assertEquals(StatusImagem.PRONTA, imagens.findById(imagemId).orElseThrow().getStatus());
        }
    }

    @Test
    void deveAguardarExclusaoAntesDeRejeitarPublicacaoConcorrente() throws Exception {
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?>[] pending = new Future<?>[1];
            new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                imagemService.excluir(profissionalId, imagemId);
                pending[0] = executor.submit(() -> versaoService.criarVersao(profissionalId, projetoId,
                        new VersaoCreateDTO(null, null, imagemId)));
                assertThrows(TimeoutException.class, () -> pending[0].get(250, TimeUnit.MILLISECONDS));
            });
            ExecutionException error = assertThrows(ExecutionException.class, () -> pending[0].get(10, TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class, error.getCause());
            assertFalse(versoes.existsByImagem_Id(imagemId));
            assertEquals(EtapaProjeto.EM_EDICAO, projetos.findById(projetoId).orElseThrow().getEtapaAtual());
        }
    }
}
