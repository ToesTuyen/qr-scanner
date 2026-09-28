# Barcode Scanner

Ứng dụng quét mã vạch cho Android, xây dựng bằng Kotlin và Jetpack Compose.
Đây là dự án Gradle độc lập; không phụ thuộc `base-application-wrapper`.

## Chức năng

- Quét QR/mã vạch bằng camera hoặc ảnh trong thư viện.
- Quét hàng loạt, lịch sử quét, yêu thích, lọc và xóa lịch sử.
- Thao tác theo loại nội dung: mở liên kết, tìm web, gọi điện, SMS, email, Wi-Fi,
  bản đồ, liên hệ, sao chép và chia sẻ.
- Thiết lập camera trước/sau, công cụ tìm kiếm, giao diện, ngôn ngữ, âm thanh,
  rung, clipboard và lịch sử.
- Chế độ Premium được bật mặc định; giao diện không hiển thị quảng cáo.

Tính năng tạo QR hiện được ẩn khỏi giao diện.

## Build

Yêu cầu Android SDK được khai báo trong `local.properties`.

```bash
./gradlew :app:assembleDebug
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`.

## Application ID

`com.ivistatect.qrscanner`
