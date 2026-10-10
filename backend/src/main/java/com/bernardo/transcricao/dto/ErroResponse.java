package com.bernardo.transcricao.dto;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public record ErroResponse(int status, String code, String message, boolean retryable) {
    public static ErroResponse de(int status) {
        return switch (status) {
            case 400 -> new ErroResponse(status, "DADOS_INVALIDOS", "Verifique os dados e o arquivo enviados.", false);
            case 401 -> new ErroResponse(status, "NAO_AUTENTICADO", "Entre novamente para continuar.", false);
            case 403 -> new ErroResponse(status, "ACESSO_NEGADO", "Acesso negado ou sessão de segurança expirada. Atualize a página.", false);
            case 404 -> new ErroResponse(status, "NAO_ENCONTRADO", "Recurso não encontrado.", false);
            case 405 -> new ErroResponse(status, "METODO_INVALIDO", "Método não permitido.", false);
            case 409 -> new ErroResponse(status, "CONFLITO", "A operação não pode ser feita no estado atual.", false);
            case 413 -> new ErroResponse(status, "ARQUIVO_GRANDE", "O arquivo excede o limite de 300 MB.", false);
            case 415 -> new ErroResponse(status, "FORMATO_INVALIDO", "Formato de requisição não suportado.", false);
            case 429 -> new ErroResponse(status, "COTA_UPLOAD", "Você atingiu o limite de arquivos de hoje. Tente novamente amanhã.", true);
            case 503 -> new ErroResponse(status, "SERVICO_INDISPONIVEL", "Serviço temporariamente indisponível. Tente novamente mais tarde.", true);
            default -> new ErroResponse(status, "ERRO_INTERNO", "Não foi possível concluir a operação. Tente novamente mais tarde.", false);
        };
    }
    /** Somente constantes definidas acima; evita serialização de exceções no filtro. */
    public static void escrever(HttpServletResponse response, int status) throws IOException {
        ErroResponse erro = de(status);
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":" + status + ",\"code\":\"" + erro.code()
                + "\",\"message\":\"" + erro.message() + "\",\"retryable\":" + erro.retryable() + "}");
    }
}
