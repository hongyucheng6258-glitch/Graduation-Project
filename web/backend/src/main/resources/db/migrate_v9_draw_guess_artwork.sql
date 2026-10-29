-- Add this nullable column after migrate_v8_draw_guess.sql on existing databases.
ALTER TABLE `draw_game_round`
  ADD COLUMN `drawing_data` MEDIUMTEXT DEFAULT NULL AFTER `snapshot_resource_id`;
