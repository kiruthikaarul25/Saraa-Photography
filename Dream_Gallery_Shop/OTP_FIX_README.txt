DREAM GALLERY SHOP - OTP FIX NOTES

1) JavaMailSender replaces the custom raw SMTP socket implementation.
2) Gmail SMTP uses smtp.gmail.com:587 with STARTTLS.
3) Set MAIL_USERNAME and MAIL_APP_PASSWORD in the same CMD window before running Gradle.
4) Use a Google App Password (not the normal Gmail sign-in password).
5) OTP expires after 5 minutes and is limited to 5 verification attempts.
6) Registration and forgot-password endpoints return safe messages instead of SMTP credentials/details.
7) The Spring Boot test class was moved under package com.shop so Spring can discover the application class.

CMD (run in the project directory; use your real local MySQL password):
  set "MYSQLUSER=root"
  set "MYSQLPASSWORD=your-local-mysql-password"
  set "MAIL_USERNAME=your-shop-gmail@gmail.com"
  set "MAIL_APP_PASSWORD=your-16-character-google-app-password"
  gradlew.bat clean build
  gradlew.bat bootRun

IMPORTANT:
- Replace the example environment variable values with your own values locally.
- Do not paste or commit Gmail App Passwords or database passwords.
- The original ZIP contained hard-coded database/mail credential values. Rotate any real credentials that were present.
