package org.ADuck;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

public final class RsaUtil {
    private static final String RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding";
    private static final int PKCS1_PADDING_BYTES = 11;

    private RsaUtil() {
    }

    public static KeyPair taoCapKhoa(int kichThuoc) throws Exception {
        if (kichThuoc != 1024 && kichThuoc != 2048) {
            throw new IllegalArgumentException("Chỉ hỗ trợ RSA 1024 hoặc 2048 bit");
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(kichThuoc);
        return generator.generateKeyPair();
    }

    public static String maHoa(String banRo, PublicKey khoa, int kichThuoc) throws Exception {
        byte[] banRoBytes = banRo.getBytes(StandardCharsets.UTF_8);
        kiemTraGioiHan(banRoBytes.length, kichThuoc);

        Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, khoa);
        byte[] banMa = cipher.doFinal(banRoBytes);

        return Base64.getEncoder().encodeToString(banMa);
    }

    public static String giaiMa(String banMa, PrivateKey khoa, int kichThuoc) throws Exception {
        Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, khoa);

        byte[] duLieu = cipher.doFinal(Base64.getDecoder().decode(banMa));
        int gioiHan = (kichThuoc / 8) - PKCS1_PADDING_BYTES;
        if (duLieu.length > gioiHan) {
            throw new IllegalArgumentException("Bản rõ vượt giới hạn RSA");
        }

        return new String(duLieu, StandardCharsets.UTF_8);
    }

    public static void kiemTraGioiHan(int soByte, int kichThuoc) {
        int gioiHan = kichThuoc == 1024 ? 117 : (kichThuoc == 2048 ? 245 : 0);
        if (gioiHan == 0) {
            throw new IllegalArgumentException("Kích thước khóa không hợp lệ");
        }
        if (soByte > gioiHan) {
            throw new IllegalArgumentException("Bản rõ tối đa " + gioiHan + " byte cho RSA " + kichThuoc);
        }
    }
}
