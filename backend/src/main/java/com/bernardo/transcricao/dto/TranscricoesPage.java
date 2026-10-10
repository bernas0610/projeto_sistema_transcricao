package com.bernardo.transcricao.dto;

import java.util.List;

public record TranscricoesPage(List<TranscricaoResumo> itens, int pagina, int totalPaginas, long total) {
}
