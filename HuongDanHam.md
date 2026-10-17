# Hướng dẫn các lớp và hàm

Tài liệu này giải thích đúng các hàm đang có trong mã nguồn. Các form độc lập, mở từ `main` tương ứng. Hầu hết thao tác TCP gọi trong `SwingWorker`; phản hồi UI được đưa về Swing EDT.

## CryptoUtil.java

| Hàm | Nhận vào | Trả về / tác dụng | Dùng ở bước |
|---|---|---|---|
| `taoKhoaTuMatKhau` | Mật khẩu | SHA-256 thành khóa AES 256 bit | AS tạo khóa dài hạn mô phỏng |
| `maHoa` | Chuỗi, khóa AES | Base64 của nonce 12 byte và ciphertext AES-GCM | AS-REP, ticket, authenticator, AP/RSA |
| `giaiMa` | Base64, khóa AES | Chuỗi rõ; lỗi nếu khóa/tag sai | Mọi nơi giải mã vé hoặc thông điệp |
| `taoKhoaPhien` | Không | Khóa AES ngẫu nhiên 256 bit | KDC tạo khóa Client-TGS/Client-Service |

AES-GCM dùng nonce ngẫu nhiên và tag xác thực 128 bit. Mật khẩu mẫu và khóa TGS/dịch vụ trong ứng dụng chỉ phục vụ demo.

## GiaoThuc.java

| Hàm/hằng | Ý nghĩa |
|---|---|
| `KDC_PORT`, `SERVICE_PORT`, `VERSION` | Cổng mặc định và phiên bản PONG |
| `SAI_LECH_TOI_DA_MS` | Sai lệch timestamp cho phép: 120.000 ms |
| `TGS_KEY_PASSWORD` | Bí mật TGS cố định của mô hình |
| `taoThongDiep` | Ghép trường thành một dòng, phân cách `|` |
| `tachThongDiep` | Tách dòng, vẫn giữ trường rỗng cuối |
| `timestamp` | Thời gian hiện tại dạng epoch milliseconds |
| `timestampHopLe` | Kiểm tra chênh lệch tối đa hai phút |
| `rutGon` | Rút gọn chuỗi dài để hiển thị |
| `noi` | Ghép phần còn lại của danh sách trường |

## RsaUtil.java

| Hàm | Tác dụng |
|---|---|
| `taoCapKhoa` | Tạo cặp RSA 1024 hoặc 2048 bit; kích thước khác bị từ chối |
| `maHoa` | Kiểm tra giới hạn byte UTF-8 rồi mã hóa RSA PKCS#1 v1.5, trả Base64 |
| `giaiMa` | Giải Base64 và giải mã bằng private key; sai khóa/bản mã tạo exception |
| `kiemTraGioiHan` | Tối đa 117 byte với 1024 bit, 245 byte với 2048 bit |

## KDCAdminForm.java

| Hàm | Vai trò |
|---|---|
| `main`, constructor, `buildControls` | Mở form, tạo bảng và điều khiển |
| `startServer`, `stopServer` | Mở/đóng ServerSocket; nhận kết nối trên worker/thread nền |
| `phucVu` | Đọc một dòng, ghi IP/lệnh, gửi phản hồi một dòng |
| `xuLy` | Điều phối PING, AS-REQ, TGS-REQ, CHECK-SERVICE |
| `xuLyAS` | Kiểm tra user, tạo khóa phiên và TGT, gửi AS-REP |
| `xuLyTGS` | Giải TGT, kiểm tra hạn/authenticator, phát Service Ticket và TGS-REP |
| `kiemTraDichVu` | Kiểm tra user chưa khóa và tên dịch vụ tồn tại |
| `themUser`, `khoaUser`, `xoaUser`, `themDichVu` | Thay đổi dữ liệu mẫu trong bộ nhớ |
| `capNhatUsers`, `ghi`, `diaChiLan` | Cập nhật bảng, log có thời gian, lấy địa chỉ hiển thị |

Tài khoản mặc định là `an/123456`, `binh/abc123`; dữ liệu quản trị mất khi tắt chương trình. Ticket hiển thị trong bảng cấp phát.

## ServiceServerForm.java

| Hàm | Vai trò |
|---|---|
| `main`, constructor, `buildControls` | Tạo UI máy chủ, port, khóa dịch vụ và địa chỉ KDC |
| `startServer`, `stopServer` | Bật/tắt socket và cập nhật trạng thái |
| `phucVu`, `xuLy` | Nhận PING/AP-REQ/RSA-SVC và định tuyến |
| `xuLyAP` | Giải Service Ticket, kiểm tra hạn/timestamp/user, replay và CHECK-SERVICE; phát AP-REP |
| `xuLyYeuCauDichVu` | Tìm phiên AP, kiểm tra timestamp/sequence/KDC rồi gọi RSA |
| `kiemTraKdc`, `gui` | Gửi CHECK-SERVICE qua TCP có timeout 3 giây |
| `xuLyLenhRsa` | Đo thời gian, gọi lệnh RSA, ghi log/bảng kết quả |
| `sinhKhoa`, `maHoaRsa`, `giaiMaRsa`, `layKhoa` | Quản lý cặp khóa RSA trong bộ nhớ; private key không trả client |
| `ghi`, `lanIp` | Log và địa chỉ LAN hiển thị |

## ClientForm.java

| Hàm | Vai trò |
|---|---|
| `main`, constructor, `buildConnectionPanel` | Tạo giao diện; kết nối host/port đặt ở đầu form |
| `ping` | PING trên worker, timeout 3 giây, đo ms và cập nhật nhãn màu |
| `setState`, `capNhatNut` | Cập nhật trạng thái và quy tắc bật/tắt AS/TGS/AP/RSA |
| `dangNhap` | AS-REQ, giải khóa phiên client và lưu TGT |
| `xinVe` | Tạo Authenticator, gửi TGS-REQ, giải TGS-REP |
| `truyCap` | Gửi AP-REQ và xác minh AP-REP timestamp + 1 |
| `guiReplay` | Gửi lại AP Authenticator đang lưu để minh họa chống replay |
| `sendRsaResult`, `sendRsa`, `replayRsa` | Tạo yêu cầu có sequence/timestamp, gọi dịch vụ RSA hoặc phát lại dòng cũ |
| `maHoaRsa`, `giaiMaRsa` | Gửi RSA command và hiển thị kết quả |
| `chayNen`, `gui` | Chạy thao tác nền; gửi/đọc một dòng TCP với timeout |
| `ngatKetNoi`, `xoaTicket` | Đưa trạng thái về đầu và hỏi có giữ ticket không |
| `ghi`, `veThongTin`, `thongBao` | Hiển thị lịch sử, ticket và lỗi ngắn gọn |

## PING và xử lý lỗi

Client gửi `PING`, máy chủ trả `PONG|tên|1.0`. `ping` gọi `gui` trên SwingWorker, đo thời gian và gọi `capNhatNut`. Lỗi socket được bắt và đưa vào nhãn/log. KDC và server cũng đọc dòng `PING` trước các yêu cầu Kerberos. TCP là luồng byte; `BufferedReader.readLine()`/`newLine()` tạo ranh giới thông điệp một dòng của ứng dụng.

## Giới hạn cần biết khi thuyết trình

Đây là bản mô phỏng tối giản. Mã nguồn hiện chưa triển khai đầy đủ tính năng đã liệt kê trong đề: bảng/quản lý dịch vụ còn tối thiểu, chưa có chức năng xóa dịch vụ trên UI; thời hạn vé không có ô chỉnh trực tiếp tương tác với ticket đã cấp; KDC chưa chống replay Authenticator TGS; ticket chưa ràng buộc đầy đủ tên dịch vụ; log màu chưa được định dạng; danh sách người truy cập/thống kê theo client còn hạn chế. Dữ liệu khóa đang dùng cố định/mẫu. Chưa có giao diện xem nội dung giải mã TGT. README nêu từng ca demo và giới hạn để không nhầm với kết quả đã chạy thật.

## Câu hỏi giảng viên có thể hỏi và gợi ý trả lời

1. **Vì sao RSA không mã hóa dữ liệu dài?** RSA với PKCS#1 v1.5 giữ 11 byte cho padding. Do đó giới hạn là modulus byte trừ 11: 117 byte ở RSA-1024 và 245 byte ở RSA-2048. Dữ liệu dài thường mã hóa bằng AES, RSA chỉ bảo vệ khóa.
2. **Vì sao khóa phiên vẫn dùng AES?** Kerberos dựa trên khóa đối xứng vì mã hóa nhanh và phù hợp bảo vệ nhiều thông điệp. Khóa phiên chỉ dùng trong một phiên/dịch vụ.
3. **Kerberos và RSA khác vai trò thế nào?** Kerberos là giao thức xác thực/cấp vé; RSA là thuật toán khóa công khai. RSA trong project là tiện ích sau xác thực, không thay thế sáu thông điệp Kerberos.
4. **Vì sao RSA-1024 không còn được khuyến nghị?** Mức an toàn của khóa 1024 bit thấp hơn chuẩn hiện đại; lựa chọn thực tế phụ thuộc chính sách và khuyến nghị hiện hành. Trong bài chỉ dùng để so sánh giới hạn.
5. **PKCS#1 v1.5 và OAEP khác nhau ra sao?** OAEP là padding mã hóa RSA hiện đại hơn và được ưu tiên cho thiết kế mới; PKCS#1 v1.5 cũ hơn, nhạy cảm với lỗi oracle nếu xử lý không cẩn thận. Bài chọn v1.5 theo đặc tả demo.
6. **Vì sao cần timeout?** Để UI/yêu cầu không chờ vô hạn khi máy đích tắt hoặc mạng không phản hồi.
7. **Vì sao chạy luồng riêng?** Socket có thể chặn; làm trên EDT sẽ khiến cửa sổ Swing không vẽ/nhận thao tác trong lúc chờ.
8. **TCP socket hoạt động thế nào?** Hai đầu tạo kết nối; ứng dụng ghi và đọc luồng byte. Ứng dụng tự quy định ranh giới dòng bằng ký tự xuống dòng.
9. **Replay được chống ra sao?** AP lưu user+timestamp Authenticator đã nhận và từ chối trùng; RSA kiểm tra sequence tăng dần theo phiên. Mô phỏng chưa lưu replay cache bền qua lần khởi động.
