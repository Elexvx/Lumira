package com.lumira.saas.modules.system.verification;

import java.io.ByteArrayOutputStream;

final class Base32Codec {

    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private Base32Codec() {
    }

    static String encode(byte[] data) {
        StringBuilder result = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte value : data) {
            buffer = (buffer << 8) | (value & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1F;
                bitsLeft -= 5;
                result.append(ALPHABET[index]);
            }
        }
        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            result.append(ALPHABET[index]);
        }
        return result.toString();
    }

    static byte[] decode(String input) {
        String normalized = input == null ? "" : input.trim().replace("=", "").replace(" ", "").toUpperCase();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (int i = 0; i < normalized.length(); i++) {
            char current = normalized.charAt(i);
            int value = decodeChar(current);
            if (value < 0) {
                continue;
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                output.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return output.toByteArray();
    }

    private static int decodeChar(char value) {
        if (value >= 'A' && value <= 'Z') {
            return value - 'A';
        }
        if (value >= '2' && value <= '7') {
            return value - '2' + 26;
        }
        return -1;
    }
}
