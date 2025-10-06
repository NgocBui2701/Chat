package com.mychat.security;

public class VigenereCipher {
    public static String encrypt(String plainText, String key) {
        StringBuilder cipherText = new StringBuilder();
        key = key.toUpperCase();
        int keyLen = key.length();
        for (int i = 0; i < plainText.length(); i++) {
            char currentChar = plainText.charAt(i);
            if (Character.isLetter(currentChar)) {
                int offset = Character.isUpperCase(currentChar) ? 'A' : 'a';
                int p = currentChar - offset;
                int k = key.charAt(i % keyLen) - 'A';
                int c = (p + k) % 26;
                cipherText.append((char)(c + offset));
            } else {
                cipherText.append(currentChar);
            }
        }
        return cipherText.toString();
    }
    
    public static String decrypt(String cipherText, String key) {
        StringBuilder plainText = new StringBuilder();
        key = key.toUpperCase();
        int keyLen = key.length();
        for (int i = 0; i < cipherText.length(); i++) {
            char currentChar = cipherText.charAt(i);
            if (Character.isLetter(currentChar)) {
                int offset = Character.isUpperCase(currentChar) ? 'A' : 'a';
                int c = currentChar - offset;
                int k = key.charAt(i % keyLen) - 'A';
                int p = (c - k + 26) % 26;
                plainText.append((char)(p + offset));
            } else {
                plainText.append(currentChar);
            }
        }
        return plainText.toString();
    }
}
