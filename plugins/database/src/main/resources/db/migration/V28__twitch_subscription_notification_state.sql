-- Neben der einmaligen Link-Bestätigung merkt sich der Server, welcher Sub-Status dem Spieler
-- zuletzt im Chat mitgeteilt wurde. NULL bedeutet: Der aktuelle Status wartet auf Zustellung.
ALTER TABLE pc_twitch_links
    ADD COLUMN subscription_notified_state BOOLEAN NULL AFTER game_notified_at;

-- Bereits bestätigte Verknüpfungen sollen nach der Migration nicht dieselbe Meldung wiederholen.
UPDATE pc_twitch_links
   SET subscription_notified_state = is_subscriber
 WHERE game_notified_at IS NOT NULL;
