package com.kinkan.take_too.security;

import com.kinkan.take_too.config.ImagemProperties;
import com.kinkan.take_too.domain.dto.*;
import com.kinkan.take_too.domain.entity.*;
import com.kinkan.take_too.domain.enums.StatusImagem;
import com.kinkan.take_too.repository.*;
import com.kinkan.take_too.service.ImagemService;
import com.kinkan.take_too.service.MarcaDaguaService;
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
class MarcaDaguaConcorrenciaPostgresTest {
    @Autowired ProfissionalRepository profissionais;
    @Autowired ImagemAssetRepository imagens;
    @Autowired PreferenciasMarcaDaguaRepository preferencias;
    @Autowired MarcaDaguaService marcas;
    @Autowired ImagemService imagemService;
    @Autowired ImagemProperties properties;
    @Autowired PlatformTransactionManager manager;
    private UUID profissionalId;
    private UUID logoId;
    @BeforeEach void preparar() {
        properties.setEnabled(true);
        var profissional=new Profissional(); profissional.setNome("Marca concorrente"); profissional.setEmail(UUID.randomUUID()+"@test.example");
        profissional.setSenhaHash("test");profissional.setPlano("PRO");profissional=profissionais.save(profissional);profissionalId=profissional.getId();
        var logo=new ImagemAsset();logo.setId(UUID.randomUUID());logo.setProfissional(profissional);logo.setNome("Logo");logo.setProvedor("fake");
        logo.setChave("imagens/"+logo.getId()+".png");logo.setTipo("image/png");logo.setChecksum("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        logo.setTamanhoBytes(100);logo.setLargura(64);logo.setAltura(32);logo.setStatus(StatusImagem.PRONTA);logoId=imagens.save(logo).getId();
    }
    private MarcaDaguaPreferenciasUpdateDTO config(int largura) {
        return new MarcaDaguaPreferenciasUpdateDTO(true,logoId,MarcaDaguaDTO.Posicao.CENTRO,largura,70,3);
    }
    @Test void deveAguardarVinculoDaLogoAntesDeRejeitarExclusao() throws Exception {
        try(var executor=Executors.newSingleThreadExecutor()) {
            Future<?>[] pending=new Future<?>[1];
            new TransactionTemplate(manager).executeWithoutResult(tx->{
                marcas.salvar(profissionalId,config(20));
                pending[0]=executor.submit(()->imagemService.excluir(profissionalId,logoId));
                assertThrows(TimeoutException.class,()->pending[0].get(250,TimeUnit.MILLISECONDS));
            });
            var error=assertThrows(ExecutionException.class,()->pending[0].get(10,TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class,error.getCause());
            assertEquals(StatusImagem.PRONTA,imagens.findById(logoId).orElseThrow().getStatus());
        }
    }
    @Test void deveAguardarExclusaoDaLogoAntesDeRejeitarVinculo() throws Exception {
        try(var executor=Executors.newSingleThreadExecutor()) {
            Future<?>[] pending=new Future<?>[1];
            new TransactionTemplate(manager).executeWithoutResult(tx->{
                imagemService.excluir(profissionalId,logoId);
                pending[0]=executor.submit(()->marcas.salvar(profissionalId,config(20)));
                assertThrows(TimeoutException.class,()->pending[0].get(250,TimeUnit.MILLISECONDS));
            });
            var error=assertThrows(ExecutionException.class,()->pending[0].get(10,TimeUnit.SECONDS));
            assertInstanceOf(IllegalArgumentException.class,error.getCause());
            assertFalse(preferencias.existsById(profissionalId));
        }
    }
    @Test void deveSerializarPrimeiraConfiguracaoSemCriarDuasLinhas() throws Exception {
        try(var executor=Executors.newSingleThreadExecutor()) {
            Future<?>[] pending=new Future<?>[1];
            new TransactionTemplate(manager).executeWithoutResult(tx->{
                marcas.salvar(profissionalId,config(20));
                pending[0]=executor.submit(()->marcas.salvar(profissionalId,config(40)));
                assertThrows(TimeoutException.class,()->pending[0].get(250,TimeUnit.MILLISECONDS));
            });
            pending[0].get(10,TimeUnit.SECONDS);
            assertEquals(40,preferencias.findById(profissionalId).orElseThrow().getLarguraPercentual());
        }
    }
    @Test void deveCapturarConfiguracaoAnteriorAntesDeAtualizacaoConcorrente() throws Exception {
        marcas.salvar(profissionalId,config(20));
        try(var executor=Executors.newSingleThreadExecutor()) {
            Future<?>[] pending=new Future<?>[1];UUID[] image=new UUID[1];
            new TransactionTemplate(manager).executeWithoutResult(tx->{
                var upload=imagemService.criarUpload(profissionalId,null,new ImagemUploadCreateDTO("Imagem","image/png",100,"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="));
                image[0]=upload.imagem().id();
                pending[0]=executor.submit(()->marcas.salvar(profissionalId,config(40)));
                assertThrows(TimeoutException.class,()->pending[0].get(250,TimeUnit.MILLISECONDS));
            });
            pending[0].get(10,TimeUnit.SECONDS);
            assertEquals(20,MarcaDaguaSnapshotDTO.fromJson(imagens.findById(image[0]).orElseThrow().getMarcaDaguaPreferenciasJson()).larguraPercentual());
            assertEquals(40,preferencias.findById(profissionalId).orElseThrow().getLarguraPercentual());
        }
    }
}
