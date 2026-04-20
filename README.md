# BankEuroB 🏦

Nowoczesna aplikacja bankowa (Premium Banking) realizująca pełne środowisko Full-Stack. Architektura podzielona na backend napisany w Javie (Spring Boot) oraz piękny, interaktywny interfejs w React (Vite, TypeScript, autorski design Glassmorphism).

## 🚀 Jak uruchomić projekt lokalnie u siebie?

Aby projekt zadziałał, musisz posiadać zainstalowane:
- **Docker** (i Docker Compose)
- **Java 17**
- **Node.js** (rekomendowane v18+)

### Krok 1: Włączenie bazy danych (Docker)
W głównym folderze projektu uruchom kontener PostgreSQL oraz opcjonalnie RabbitMQ wpisując polecenie:
```bash
docker compose up -d
```
> [!NOTE]
> Baza PostgreSQL wystartuje na porcie **5433** (hasło `root`, użytkownik `root`), dzięki czemu nie wejdzie w konflikt z lokalnymi instalacjami na Twoim komputerze.

### Krok 2: Uruchomienie Backend'u (Spring Boot)
Otwórz terminal w folderze `backend` i użyj dołączonego narzędzia Gradle (pobierze on wszystkie pakiety oraz zainstaluje schematy w bazie poprzez Flyway):

**Windows:**
```bash
cd backend
.\gradlew.bat bootRun
```
**Mac/Linux:**
```bash
cd backend
./gradlew bootRun
```
> [!IMPORTANT]
> Poczekaj aż konsola wypisze 🟢 `Started BankEuroBApplication in X seconds`. Przy pierwszym starcie system automatycznie wstrzyknie testowe konta (m.in. `anna.kowalski@example.com`).

### Krok 3: Uruchomienie Frontend'u (React + Vite)
Otwórz nowy, osobny terminal w folderze `frontend`. Zainstaluj pakiety i wystartuj aplikację:
```bash
cd frontend
npm install
npm run dev
```

### Krok 4: Gotowe! 🎉
Aplikacja jest już w pełni funkcjonalna:
- 🔥 Strona (Frontend) dostępna jest pod adresem: [http://localhost:5173/](http://localhost:5173/)
- ⚙️ Endpointy API (Backend) działają pod adresem: `http://localhost:8080/`

**Dane konta testowego:**
- Email: `anna.kowalski@example.com`
- Hasło: `password123`
