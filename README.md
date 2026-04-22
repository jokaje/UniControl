UniControl 📱🏠🤖

UniControl ist eine zentrale, native Android-Appliance, die Smart Home, persönliche Medienverwaltung und Künstliche Intelligenz in einer einzigen, modernen Oberfläche vereint.

✨ Features

📸 Immich-Integration (Vollständig): * Betrachte deine Fotos, Alben, Personen und generierte "Erinnerungen" (Memories).

Automatisches Foto-Backup im Hintergrund (via Android WorkManager).

🤖 Echo (OpenClaw KI-Assistent): * Ein nativer Chat-Bereich zur direkten Kommunikation mit einem OpenClaw-Backend für smarte Assistenz und Haussteuerung.

🏠 Home Assistant WebUI: * Nahtlose Einbindung deines Home Assistant Dashboards direkt in der App via WebView.

📡 NFC-Steuerung: * Integrierter NFC-Handler zum Auslösen von spezifischen Automatisierungen oder Aktionen über NFC-Tags.

📍 Standort-Tracking: * Hintergrund-Standortaktualisierungen (LocationWorker) zur Nutzung für Präsenzerkennung im Smart Home.

🛠️ Technologien & Architektur

Sprache: Java

Architektur: MVVM-Ansatz (teilweise) mit einem SharedViewModel zur Kommunikation zwischen Fragmenten.

UI-Komponenten: Bottom Navigation, dynamische Bottom Sheets für Einstellungen und Filter, native RecyclerViews für Chat und Galerien.

Hintergrundprozesse: WorkManager für verlässliche Location-Updates und Foto-Backups.

Sicherheit: Eigene CryptoUtils zur sicheren Speicherung von Tokens und Zugangsdaten.

🚀 Voraussetzungen & Installation

Um diese App zu kompilieren und zu nutzen, benötigst du eigene Instanzen der folgenden Dienste:

Immich Server: Für die Fotoverwaltung und Backups.

Home Assistant: Für das WebUI-Dashboard.

OpenClaw Backend: Für die KI-Funktionalitäten im Bereich "Echo".

Solltest du diese dienste nicht haben, kannst du auch einzelne  Ansichten ausblenden. Sehe nur das was du brauchst!

Setup:

Klone das Repository in Android Studio.

Baue das Projekt mit Gradle (./gradlew build).

Trage beim ersten Start in der App in den Einstellungen (Settings Bottom Sheet) deine jeweiligen Server-URLs und API-Keys ein.

📄 Lizenz

Dieses Projekt ist unter der MIT Lizenz lizenziert. Weitere Details findest du in der https://www.google.com/search?q=LICENSE Datei.

Hinweis: Diese App nutzt Schnittstellen zu Immich, OpenClaw und Home Assistant. Diese Projekte haben jeweils ihre eigenen Lizenzen.
