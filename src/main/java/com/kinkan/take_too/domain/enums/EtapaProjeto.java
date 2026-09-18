package com.kinkan.take_too.domain.enums;

public enum EtapaProjeto {
    GRAVACAO_CONCLUIDA,
    EM_EDICAO,
    REVISAO_CLIENTE,
    APROVADO,
    ENTREGUE;

    public boolean podeTransitarPara(EtapaProjeto destino) {
        if (this == destino) {
            return true;
        }
        return switch (this) {
            case GRAVACAO_CONCLUIDA -> destino == EM_EDICAO;
            case EM_EDICAO -> destino == REVISAO_CLIENTE;
            case REVISAO_CLIENTE -> destino == APROVADO || destino == EM_EDICAO;
            case APROVADO -> destino == ENTREGUE || destino == EM_EDICAO;
            case ENTREGUE -> false;
        };
    }
}
