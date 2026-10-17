# Hồ sơ yêu cầu và hướng dẫn viết báo cáo

## 1. Đặc tả project

### 1.1 Mục tiêu và công nghệ

Project minh họa giao thức xác thực Kerberos bằng ba ứng dụng Java Swing độc lập: Client, KDC đảm nhiệm AS/TGS, và Service Server. Dùng Java 17, thư viện JDK, TCP Socket và AES/GCM/NoPadding. Mã hóa mật khẩu thành khóa dài hạn bằng SHA-256; khóa phiên AES sinh ngẫu nhiên. Mã nguồn package `org.ADuck`, đặt tại `src/main/java/org/ADuck/`. Không dùng JSON/serialization; thông điệp là một dòng, phân tách bằng `|`, ciphertext được biểu diễn Base64. Ba form bắt buộc mỗi form có `main`: `ClientForm.java`, `ServiceServerForm.java`, `KDCAdminForm.java`. File phụ dùng chung tối đa gồm `CryptoUtil.java`, `GiaoThuc.java`, `RsaUtil.java`. Tên hàm/biến tự giải thích, không comment Java; hàm nên ngắn và chỉ làm một nhiệm vụ.

### 1.2 Sáu thông điệp Kerberos

| # | Thông điệp | Chiều | Nội dung và bảo vệ |
|---|---|---|---|
| 1 | AS-REQ | Client → KDC | Tên người dùng |
| 2 | AS-REP | KDC → Client | Khóa Client-TGS và thời hạn mã hóa bằng khóa dài hạn client; TGT có client, khóa phiên và hạn, mã hóa bằng khóa TGS |
| 3 | TGS-REQ | Client → KDC | TGT, Authenticator gồm tên và timestamp mã hóa bằng Client-TGS, tên dịch vụ |
| 4 | TGS-REP | KDC → Client | Khóa Client-Service mã hóa bằng Client-TGS; Service Ticket mã hóa bằng khóa dịch vụ |
| 5 | AP-REQ | Client → Service Server | Service Ticket và Authenticator mới mã hóa bằng Client-Service |
| 6 | AP-REP | Service Server → Client | Timestamp + 1 mã hóa bằng Client-Service để client xác minh server |

Mô hình yêu cầu TGT mặc định 10 phút, Service Ticket 5 phút; kiểm tra hết hạn, timestamp với độ lệch tối đa hai phút, chống replay, khớp tên Authenticator/ticket. Tài khoản mẫu `an/123456`, `binh/abc123`; FileServer có khóa bí mật. KDC mặc định 8888, dịch vụ 9999.

### 1.3 Giao diện và kết nối

Client đặt khu vực kết nối ở đầu form, host/port nhập được cho cả KDC và dịch vụ. PING/PONG có tên/version, ms, trạng thái màu; timeout ba giây; thao tác mạng chạy nền. AS/TGS phụ thuộc PING KDC; AP phụ thuộc PING server và ticket; RSA phụ thuộc AP-REP. Có hiển thị vé/khóa/hạn, xóa ticket/lịch sử, ngắt kết nối và tùy chọn replay. KDC quản lý user (thêm/xóa/đổi mật khẩu/khóa/mở), dịch vụ và thời hạn vé; theo dõi ticket cấp, kết nối và lỗi. Service Server hiển thị trạng thái/cổng/IP LAN, kết nối, ticket và lịch sử yêu cầu.

### 1.4 RSA sau xác thực

Chỉ phiên AP-REP mới được gọi dịch vụ RSA. Một yêu cầu chứa `user|sequence|timestamp|lệnh|kích thước|payloadBase64`, được AES-GCM bằng khóa Client-Service rồi Base64. Server kiểm tra phiên, hạn ticket, timestamp, sequence và trạng thái user/dịch vụ hỏi từ KDC rồi mới xử lý. Hỗ trợ `SINH_KHOA`, `MA_HOA`, `GIAI_MA`, RSA 1024/2048 với RSA/ECB/PKCS1Padding, khóa riêng chỉ ở server. Giới hạn rõ 117/245 byte; bản mã 128/256 byte. Dữ liệu ngoài giới hạn, khóa chưa sinh, bản mã sai và replay phải bị từ chối có thông báo.

### 1.5 Ca kiểm thử yêu cầu

(a) AS → TGS → AP thành công; (b) sai mật khẩu; (c) ticket hết hạn; (d) replay Authenticator AP; (e) khóa user; (f) chặn RSA trước AP; (g) RSA-1024 với “Xin chao Kerberos”; (h) RSA-2048 với chuỗi dài hơn, so sánh kích thước và thời gian log thật; (i) quá giới hạn rõ; (j) replay RSA; (k) sai cỡ khóa; (l) KDC tắt và PING lỗi; (m) bật KDC/PONG/AS; (n) sai cổng dịch vụ; (o) tắt server giữa RSA rồi bật lại/PING/AP; (p) hai máy qua IP LAN. Không ghi là đã đạt nếu thiếu log/ảnh chạy thực tế.

### 1.6 Tài liệu và đầu ra

Phải có `README.md`, `HuongDanHam.md`, `Kerberos.md` ở thư mục gốc. Báo cáo Word nếu có `Trang_bia_mau__1_.docx`, giữ mẫu, chỉ thay tên đề tài/học phần, nội dung trung thực, bổ sung hình/bảng, trang và trích dẫn đúng. Mô tả bản mô phỏng tách biệt Kerberos chuẩn (ưu tiên RFC 4120). Mã nguồn vừa tạo chỉ là bản tối thiểu; đối chiếu các hạn chế được ghi cuối `HuongDanHam.md` trước khi viết kết quả hoặc khẳng định chức năng.

## 2. Prompt viết báo cáo

Thay các nội dung trong ngoặc vuông bằng bằng chứng thật. Với mọi prompt, tuyệt đối không tự tạo log, kết quả, URL, DOI, nguồn hoặc số liệu. Nếu thiếu thì ghi đúng `[cần bổ sung]` hoặc “Cần chạy và bổ sung”. Trích dẫn chỉ dùng tài liệu đã mở và xác minh.

### Chương 1 – Mở đầu (1.200–1.500 từ)

> Viết Chương 1 bằng tiếng Việt học thuật, dài 1.200–1.500 từ. Trình bày bối cảnh xác thực mạng, rủi ro khi mật khẩu được gửi lặp lại, động cơ chọn Kerberos; mục tiêu tổng quát/cụ thể; đối tượng và phạm vi; phương pháp khảo cứu, phân tích, thiết kế, cài đặt và kiểm thử; giới hạn của mô hình Java Swing; bố cục sáu chương. Phân biệt rõ Kerberos chuẩn với sản phẩm mô phỏng; không nói rằng hệ thống an toàn tuyệt đối. Không bịa số liệu hay tài liệu tham khảo. Đánh dấu `[cần bổ sung: thông tin môn học/đơn vị nếu cần]` ở vị trí cần người viết điền.

### Chương 2 – Cơ sở lý thuyết (2.800–3.500 từ)

> Viết 2.800–3.500 từ, văn phong học thuật tiếng Việt, trích dẫn số theo tài liệu đã kiểm chứng. Giải thích mô hình tin cậy, client, AS, TGS, dịch vụ; TGT, Service Ticket, Authenticator; khóa dài hạn và khóa phiên; lần lượt giải thích sáu thông điệp và bảng Kerberos v4/v5. Phân tích ưu/nhược điểm, đồng bộ thời gian, replay và cách phòng chống; trình bày Golden Ticket, Kerberoasting và biện pháp giảm thiểu ở mức khái niệm, không hướng dẫn xâm nhập. Ưu tiên RFC 4120 và đối chiếu rõ phần nào là chuẩn, phần nào là mô phỏng. Không suy diễn rằng code demo có các cơ chế bảo vệ production.

### Mục RSA gắn với Kerberos trong Chương 2 (1.800–2.400 từ)

> Viết 1.800–2.400 từ học thuật về RSA, cặp khóa, modulus và padding; mô tả chính xác `RSA/ECB/PKCS1Padding`, công thức giới hạn plaintext modulus-bytes trừ 11, các mức 117/245 byte và ciphertext 128/256 byte. So sánh RSA 1024/2048 về kích thước và giới hạn; không kết luận benchmark tốc độ nếu chưa có log đo thật, để `[cần bổ sung log đo]`. Giải thích vì sao RSA không phù hợp dữ liệu dài, vì sao Kerberos truyền thống chủ yếu dùng mật mã đối xứng, và vì sao tiện ích RSA trong demo chỉ mở sau AP-REP. Nêu rõ đây là phần mở rộng ứng dụng, không thuộc sáu thông điệp Kerberos chuẩn. Cảnh báo RSA-1024 và PKCS#1 v1.5 chỉ dùng minh họa, không khuyến nghị cho hệ thống mới. Không bịa trích dẫn; đánh dấu `[cần kiểm tra nguồn]`.

### Chương 3 – Phân tích và thiết kế (2.000–2.600 từ)

> Viết 2.000–2.600 từ, nêu yêu cầu chức năng và phi chức năng, tác nhân, kiến trúc ba vai trò, sơ đồ thành phần/tuần tự, mô hình dữ liệu lưu trong bộ nhớ, định dạng dòng và Base64. Lập bảng trường của sáu thông điệp; mô tả quản lý khóa, timestamp lệch tối đa hai phút, ticket expiry, replay cache/sequence và PING/PONG timeout. Đối chiếu từng thiết kế với code hiện tại và ghi hạn chế ở `[cần bổ sung hoặc chưa triển khai]`. Không trình bày thiết kế mong muốn như tính năng đã chạy.

### Chương 4 – Cài đặt và giao diện (2.000–2.800 từ)

> Viết 2.000–2.800 từ dựa trực tiếp trên các file `CryptoUtil`, `GiaoThuc`, `RsaUtil`, `KDCAdminForm`, `ServiceServerForm`, `ClientForm`. Giải thích Java 17, Swing, Socket, worker/thread, từng khu vực giao diện và ánh xạ từng thông điệp sang tên phương thức thật: `xuLyAS`, `xuLyTGS`, `xuLyAP`, `xuLyYeuCauDichVu`, `sendRsaResult` (kiểm tra tên thực trong code). Nêu cách chạy ba form và cổng mặc định. Ghi chính xác hạn chế: khóa mẫu cố định, lưu bộ nhớ, không phải triển khai Kerberos chuẩn; không tuyên bố về bảng/log/chức năng UI chưa tồn tại. Chèn ảnh chạy thật hoặc placeholder đánh dấu rõ.

### Chương 5 – Kết quả thử nghiệm (1.500–2.000 từ)

> Viết 1.500–2.000 từ. Tạo bảng cột: mã ca, tiền điều kiện, thao tác, kết quả mong đợi, quan sát/log/ảnh thật, đạt/chưa đạt. Bao gồm ca (a)–(p) của đặc tả. Chỉ điền kết quả quan sát được từ log hoặc ảnh gốc; mọi ca chưa chạy phải ghi nguyên văn “Cần chạy và bổ sung”. Số đo thời gian RSA chỉ trích từ lần chạy cụ thể, nêu môi trường và cách đo; không ngoại suy. Không bịa thông tin máy, số lần chạy, ms hoặc ảnh. Nêu sai khác giữa yêu cầu và code.

### Chương 6 – Kết luận và hướng phát triển (800–1.100 từ)

> Viết 800–1.100 từ, tổng kết mục tiêu nào đạt theo bằng chứng, kiến thức rút ra và giới hạn còn lại. Đề xuất hướng phát triển cụ thể: quản lý bí mật an toàn, giao thức chuẩn, cache replay có thời hạn, cấu hình ticket, kiểm thử tự động, giao diện log màu và đo hiệu năng lặp lại. Không khẳng định an toàn tuyệt đối hoặc thành công ở ca chưa chạy. Tách nhận xét cá nhân khỏi kết quả kỹ thuật.

### Prompt tài liệu tham khảo

> Lập danh mục tài liệu tham khảo kiểu đánh số, chỉ dùng nguồn đã truy cập và xác minh được nội dung, tác giả, năm, tiêu đề, nhà xuất bản và URL/DOI. Ưu tiên RFC 4120, RFC 1510, RFC 8017, bài Kerberos gốc của Steiner, Neuman, Schiller (1988), Neuman và Ts'o (1994), tài liệu Oracle về JCA, Swing và Socket, NIST SP 800-57 và SP 800-131A. Nếu chưa xác minh trường nào, ghi `[cần kiểm tra]` và không tự đoán. Đảm bảo mọi trích dẫn [n] trong bài khớp chính xác danh mục.

### Prompt tổng hợp và biên tập

> Ghép các chương thành báo cáo nhất quán; thống nhất Kerberos, KDC, AS, TGS, TGT, Service Ticket, Authenticator, khóa phiên. Kiểm tra độ dài từng prompt, số thứ tự hình/bảng, trích dẫn, tính trung thực với code và kết quả. Giữ nguyên `[cần bổ sung]`, “Cần chạy và bổ sung” và `[cần kiểm tra]`; không tự điền. Không thêm ảnh/log/số liệu giả. Kiểm tra mọi lời khẳng định chức năng bằng source hiện tại; nếu code chưa có thì sửa lời mô tả hoặc nêu hạn chế.

## 3. Dàn ý đầy đủ báo cáo

1. Trang bìa theo mẫu; để trống thông tin cá nhân.
2. Lời cảm ơn.
3. Tóm tắt và từ khóa.
4. Mục lục.
5. Danh mục hình.
6. Danh mục bảng.
7. Danh mục từ viết tắt.
8. Chương 1. Mở đầu.
9. Chương 2. Cơ sở lý thuyết (gồm mục RSA).
10. Chương 3. Phân tích và thiết kế (tích hợp kết nối/RSA).
11. Chương 4. Cài đặt và giao diện.
12. Chương 5. Kết quả thử nghiệm.
13. Chương 6. Kết luận và hướng phát triển.
14. Tài liệu tham khảo.
15. Phụ lục (mã nguồn/biên bản chạy phù hợp).

## 4. Danh sách 25 hình cần chèn

Ảnh giao diện phải chụp từ lần chạy thật, không sửa log. Nếu chưa có bằng chứng, để placeholder vàng nhạt và ghi “Cần chạy và bổ sung”. Mỗi ảnh phải được dẫn trong nội dung, chú thích đánh số theo chương.

1. Sơ đồ kiến trúc Client–KDC–Service Server.
2. Sơ đồ tuần tự sáu thông điệp Kerberos.
3. KDC lúc khởi động.
4. Client sau AS-REP.
5. Client sau TGS-REP.
6. AP thành công.
7. Sai mật khẩu hoặc user không tồn tại.
8. Ticket hết hạn.
9. Replay Authenticator bị từ chối.
10. Tài khoản bị khóa.
11. Bảng ticket KDC.
12. RSA bị khóa trước AP.
13. RSA bật sau AP-REP.
14. Bảng khóa RSA trên server, không lộ private key.
15. Bảng yêu cầu và thống kê RSA.
16. RSA-1024 mã hóa/giải mã “Xin chao Kerberos”.
17. So sánh bản mã 1024/2048 (128/256 byte) và thời gian đo thật.
18. Lỗi plaintext quá giới hạn và giải mã sai cỡ khóa.
19. Replay RSA bị từ chối.
20. Client chưa kết nối (xám).
21. PING thành công (xanh, tên/version/ms).
22. Lỗi kết nối khi KDC chưa bật.
23. Sai cổng Service Server.
24. KDC/Server chạy với IP LAN, cổng, số kết nối.
25. Client từ máy khác nhập IP LAN.
