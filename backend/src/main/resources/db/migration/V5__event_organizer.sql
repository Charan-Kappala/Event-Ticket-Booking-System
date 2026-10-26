-- Each event belongs to the organizer who created it; only they can edit or delete it.
-- Events created before this migration have no owner (NULL) and can't be edited through the API.

ALTER TABLE events
    ADD COLUMN organizer_id BIGINT,
    ADD CONSTRAINT fk_events_organizer FOREIGN KEY (organizer_id) REFERENCES users (id);
