package org.ADuck;

import java.time.Instant;
import java.util.Arrays;

public final class GiaoThuc {
    public static final int KDC_PORT = 8888;
    public static final int SERVICE_PORT = 9999;
    public static final String VERSION = "1.0";
    public static final long SAI_LECH_TOI_DA_MS = 120000;
    public static final String TGS_KEY_PASSWORD = "Kerberos-TGS-demo-key";

    private GiaoThuc() {
    }

    public static String taoThongDiep(String... cacTruong) {
        return String.join("|", cacTruong);
    }

    public static String[] tachThongDiep(String thongDiep) {
        if (thongDiep == null) {
            return new String[0];
        }
        return thongDiep.split("\\|", -1);
    }

    public static String noi(String[] mang, int batDau) {
        if (mang == null || batDau >= mang.length) {
            return "";
        }
        return String.join("|", Arrays.copyOfRange(mang, batDau, mang.length));
    }

    public static String timestamp() {
        return Long.toString(Instant.now().toEpochMilli());
    }

    public static boolean timestampHopLe(long thoiGian) {
        return Math.abs(System.currentTimeMillis() - thoiGian) <= SAI_LECH_TOI_DA_MS;
    }

    public static String rutGon(String duLieu) {
        if (duLieu == null || duLieu.length() < 28) {
            return duLieu;
        }
        return duLieu.substring(0, 12) + "..." + duLieu.substring(duLieu.length() - 12);
    }
}
