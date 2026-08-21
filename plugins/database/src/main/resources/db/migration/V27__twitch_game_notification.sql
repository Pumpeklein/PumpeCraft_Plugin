-- Die Website und der Minecraft-Server laufen getrennt. Dieser Zeitstempel dient als persistente
-- Zustellbestätigung, damit ein erfolgreicher Twitch-Link genau einmal im Spiel bestätigt wird.
ALTER TABLE pc_twitch_links
    ADD COLUMN game_notified_at BIGINT NULL AFTER subscription_checked_at;
