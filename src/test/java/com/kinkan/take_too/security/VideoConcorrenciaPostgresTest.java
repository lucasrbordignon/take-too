package com.kinkan.take_too.security;

import com.kinkan.take_too.domain.dto.VersaoCreateDTO;
import com.kinkan.take_too.domain.entity.*;
import com.kinkan.take_too.domain.enums.EtapaProjeto;
import com.kinkan.take_too.domain.enums.StatusVideo;
import com.kinkan.take_too.repository.*;
import com.kinkan.take_too.service.VideoService;
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

@SpringBootTest(properties = {"app.video.enabled=true", "app.video.provider=fake"})
@Import(VideoIntegrationTest.ProvidersConfiguration.class)
@EnabledIfSystemProperty(named = "taketoo.postgres", matches = "true")
class VideoConcorrenciaPostgresTest {
    @Autowired private ProfissionalRepository profissionais;
    @Autowired private ClienteRepository clientes;
    @Autowired private ProjetoRepository projetos;
    @Autowired private VideoAssetRepository videos;
    @Autowired private VersaoRepository versoes;
    @Autowired private VideoService videoService;
    @Autowired private VersaoService versaoService;
    @Autowired private PlatformTransactionManager transactionManager;
    private UUID profissionalId;
    private UUID projetoId;
    private UUID videoId;

    @BeforeEach
    void preparar() {
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
        VideoAsset video = new VideoAsset();
        video.setId(UUID.randomUUID());
        video.setProfissional(profissional);
        video.setNome("Pronto");
        video.setProvedor("fake");
        video.setStatus(StatusVideo.PRONTO);
        video.setPlaybackExternoId("playback-test");
        video.setUploadExternoId("upload-" + video.getId());
        videoId = videos.save(video).getId();
    }

    @Test
    void deveAguardarPublicacaoAntesDeRejeitarExclusaoConcorrente() throws Exception {
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?>[] pending = new Future<?>[1];
            new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                versaoService.criarVersao(profissionalId, projetoId, new VersaoCreateDTO(null, videoId));
                pending[0] = executor.submit(() -> videoService.excluir(profissionalId, videoId));
                assertThrows(TimeoutException.class, () -> pending[0].get(250, TimeUnit.MILLISECONDS));
            });
            ExecutionException error = assertThrows(ExecutionException.class, () -> pending[0].get(10, TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class, error.getCause());
            assertTrue(versoes.existsByVideo_Id(videoId));
            assertEquals(StatusVideo.PRONTO, videos.findById(videoId).orElseThrow().getStatus());
        }
    }

    @Test
    void deveAguardarExclusaoAntesDeRejeitarPublicacaoConcorrente() throws Exception {
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?>[] pending = new Future<?>[1];
            new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                videoService.excluir(profissionalId, videoId);
                pending[0] = executor.submit(() -> versaoService.criarVersao(profissionalId, projetoId,
                        new VersaoCreateDTO(null, videoId)));
                assertThrows(TimeoutException.class, () -> pending[0].get(250, TimeUnit.MILLISECONDS));
            });
            ExecutionException error = assertThrows(ExecutionException.class, () -> pending[0].get(10, TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class, error.getCause());
            assertFalse(versoes.existsByVideo_Id(videoId));
            assertEquals(EtapaProjeto.EM_EDICAO, projetos.findById(projetoId).orElseThrow().getEtapaAtual());
        }
    }
}
