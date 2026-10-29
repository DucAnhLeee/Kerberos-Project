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
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int NONCE_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private CryptoUtil() {
    }

    public static SecretKey taoKhoaTuMatKhau(String matKhau) throws Exception {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha256.digest(matKhau.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(hash, "AES");
    }

    public static SecretKey taoKhoaPhien() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256);
        return keyGenerator.generateKey();
    }

    public static String maHoa(String duLieu, SecretKey khoa) throws Exception {
        byte[] nonce = new byte[NONCE_LENGTH];
        RANDOM.nextBytes(nonce);

        Cipher cipher = Cipher.getInstance(AES_GCM);
        cipher.init(Cipher.ENCRYPT_MODE, khoa, new GCMParameterSpec(GCM_TAG_LENGTH, nonce));
        byte[] banMa = cipher.doFinal(duLieu.getBytes(StandardCharsets.UTF_8));

        ByteBuffer buffer = ByteBuffer.allocate(nonce.length + banMa.length);
        buffer.put(nonce);
        buffer.put(banMa);

        return Base64.getEncoder().encodeToString(buffer.array());
    }

    public static String giaiMa(String duLieuMaHoa, SecretKey khoa) throws Exception {
        byte[] duLieu = Base64.getDecoder().decode(duLieuMaHoa);
        if (duLieu.length < NONCE_LENGTH) {
            throw new IllegalArgumentException("Dữ liệu mã hóa không hợp lệ");
        }

        byte[] nonce = new byte[NONCE_LENGTH];
        System.arraycopy(duLieu, 0, nonce, 0, NONCE_LENGTH);

        int doDaiBanMa = duLieu.length - NONCE_LENGTH;
        byte[] banMa = new byte[doDaiBanMa];
        System.arraycopy(duLieu, NONCE_LENGTH, banMa, 0, doDaiBanMa);

        Cipher cipher = Cipher.getInstance(AES_GCM);
        cipher.init(Cipher.DECRYPT_MODE, khoa, new GCMParameterSpec(GCM_TAG_LENGTH, nonce));
        byte[] banRo = cipher.doFinal(banMa);

        return new String(banRo, StandardCharsets.UTF_8);
    }
}
