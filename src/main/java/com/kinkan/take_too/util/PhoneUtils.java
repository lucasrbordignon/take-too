package com.kinkan.take_too.util;

public final class PhoneUtils {

    private PhoneUtils() {
    }

    /**
     * Normaliza e valida um número de telefone brasileiro/E.164.
     * Exige que o número contenha DDD e formato válido (10 ou 11 dígitos nacionais, ou 12/13 dígitos com DDI 55).
     * Exemplos válidos:
     * "(11) 98765-4321" -> "5511987654321"
     * "+55 11 98765-4321" -> "5511987654321"
     * "11987654321" -> "5511987654321"
     *
     * Entradas sem dígitos válidos (ex: "abc", "123") lançam IllegalArgumentException.
     */
    public static String normalize(String rawPhone) {
        if (rawPhone == null || rawPhone.trim().isEmpty()) {
            throw new IllegalArgumentException("O telefone não pode ser vazio.");
        }
        String digits = rawPhone.replaceAll("\\D", "");
        if (digits.length() == 10 || digits.length() == 11) {
            return "55" + digits;
        }
        if (digits.startsWith("55") && (digits.length() == 12 || digits.length() == 13)) {
            return digits;
        }
        throw new IllegalArgumentException(
                "Número de telefone inválido. Informe um telefone brasileiro válido com DDD (ex: (11) 98765-4321).");
    }
}
