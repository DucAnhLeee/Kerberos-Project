package org.ADuck;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class CryptoUtil {
    private static final SecureRandom RANDOM = new SecureRandom();

    private CryptoUtil() {
    }

    public static SecretKey taoKhoaTuMatKhau(String matKhau) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(matKhau.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(hash, "AES");
    }

    public static String maHoa(String duLieu, SecretKey khoa) throws Exception {
        byte[] nonce = new byte[12];
        RANDOM.nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, khoa, new GCMParameterSpec(128, nonce));
        byte[] encrypted = cipher.doFinal(duLieu.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
    }

    public static String giaiMa(String duLieu, SecretKey khoa) throws Exception {
        byte[] data = Base64.getDecoder().decode(duLieu);
        byte[] nonce = new byte[12];
        System.arraycopy(data, 0, nonce, 0, nonce.length);
        byte[] encrypted = new byte[data.length - nonce.length];
        System.arraycopy(data, nonce.length, encrypted, 0, encrypted.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, khoa, new GCMParameterSpec(128, nonce));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    public static SecretKey taoKhoaPhien() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        return generator.generateKey();
    }
}
