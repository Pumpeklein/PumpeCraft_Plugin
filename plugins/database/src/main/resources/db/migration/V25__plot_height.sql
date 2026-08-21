-- Höhenbegrenzung für Grundstücke. Leer heißt: von der untersten bis zur obersten Schicht, und
-- das bleibt der Normalfall - nur das Team schränkt die Höhe ein, etwa um unter dem Spawn noch
-- graben zu lassen.
ALTER TABLE pc_plots
    ADD COLUMN min_y INT NULL AFTER max_z,
    ADD COLUMN max_y INT NULL AFTER min_y;
