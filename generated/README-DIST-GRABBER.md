# DIST Grabber

Android app for reading publicly displayed district/DIST options from the Taichung Cloud School website.

- Opens https://school.tc.edu.tw in an embedded WebView.
- Attempts to open the public "縣市帳號登入" entry and select 臺中市 when available.
- Scans visible district-related select options and explicit DIST references in inline scripts.
- Highlights any option labeled 大里區.
- Copies and shares the collected JSON.

The app does not sign in, request credentials, bypass authentication, or modify server data. The DIST value is only confirmed when the target website exposes it in the public page.
