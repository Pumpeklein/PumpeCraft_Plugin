# SubEssentials

Verknüpft Minecraft-Spieler über die Support-Website mit Twitch und schaltet für Abonnenten
virtuelle Enderchest-, Werkbank-, Amboss- und Zaubertisch-Menüs frei.

## Twitch-Konfiguration

Der Serverprozess liest `TWITCH_CLIENT_ID`, `TWITCH_AUTH_TOKEN` und `TWITCH_USER_LOGIN` aus der
Umgebung. Dieselben Werte benötigt die Website. Das Kanal-Token braucht für die regelmäßige
Prüfung den Twitch-Scope `channel:read:subscriptions`. Werte aus `config.yml` dienen nur als
lokaler Fallback und sollten in Produktion leer bleiben.

Die regelmäßige Abo-Prüfung umfasst alle verknüpften Twitch-Konten, auch wenn der zugehörige
Minecraft-Spieler offline ist. Statuswechsel werden gespeichert und beim nächsten Login
mitgeteilt; online Spieler erhalten die Änderung direkt nach der Prüfung.

Minecraft kann Browser aus Sicherheitsgründen nicht ungefragt öffnen. `/twitch link` sendet
deshalb einen deutlich markierten, anklickbaren Link mit Hover-Text.
