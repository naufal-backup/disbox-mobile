# Disbox Web ⬡ (Cloud-Native Edition)

Disbox Web adalah edisi serverless dari Disbox, memungkinkan Anda mengelola penyimpanan Discord tak terbatas langsung melalui browser tanpa instalasi. Kini hadir dengan dukungan **Supabase** untuk sinkronisasi profil dan struktur drive yang lebih cepat.

## 🚀 Fitur Web

*   **🌐 Akses Tanpa Instalasi:** Kelola drive Anda dari perangkat mana saja melalui browser.
*   **☁️ Supabase Powered:** Struktur file dan folder disimpan secara otomatis di database Cloud (Database-First).
*   **🔐 3-Mode Login System:**
    *   **Account Login:** Sinkronisasi drive lintas perangkat via profil cloud.
    *   **Account Register:** Buat profil baru dan amankan data Anda di database.
    *   **Guest Mode:** Masuk instan hanya dengan Webhook (Metadata disimpan secara lokal di browser).
*   **🛡️ Keamanan Terjamin:**
    *   Password di-hash (SHA-256).
    *   Webhook di-enkripsi (AES-256).
    *   Metadata di-enkripsi di sisi klien.
*   **⚡ Sinkronisasi Latar Belakang:** Aksi buat folder dan kelola file diproses secara otomatis di latar belakang.
*   **📂 Virtual File System:** Struktur folder rapi dan intuitif.

## 🛠 Teknologi

*   **Frontend:** React, Vite, Tailwind-like CSS.
*   **Backend:** Vercel Serverless Functions.
*   **Database:** Supabase (PostgreSQL).
*   **Storage:** Discord Webhook.

## ⚙️ Pengembangan Lokal

1.  **Kloning:**
    ```bash
    git clone https://github.com/naufal-backup/disbox-web.git
    cd disbox-web
    ```
2.  **Install & Run:**
    ```bash
    npm install
    npm run dev
    ```

## 🔒 Keamanan & Privasi

Disbox Web menjaga kerahasiaan data Anda dengan tidak pernah menyimpan Webhook asli dalam bentuk teks biasa di database. Seluruh struktur file didekripsi hanya di browser Anda menggunakan kunci unik.

---

**Developed by Naufal Gastiadirrijal Fawwaz Alamsyah**
