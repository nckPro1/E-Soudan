-- Báo cáo chuyển từ 3 ô văn bản (đã làm / kế hoạch / vướng mắc) sang MỘT thân bài rich-text (HTML đã lọc ở server).
-- Báo cáo cũ được dựng lại thành HTML với ba tiêu đề H2 tương ứng để không mất dữ liệu.

ALTER TABLE report_post ADD COLUMN body TEXT;

-- Thoát ký tự HTML và đổi xuống dòng thành <br> (hàm tạm, chỉ sống trong phiên chạy migration)
CREATE FUNCTION pg_temp.esc(t text) RETURNS text AS $$
    SELECT replace(replace(replace(replace(t, '&', '&amp;'), '<', '&lt;'), '>', '&gt;'), E'\n', '<br>')
$$ LANGUAGE sql IMMUTABLE;

UPDATE report_post
SET body = '<h2>Việc đã làm</h2><p>' || pg_temp.esc(done_text) || '</p>'
        || CASE WHEN plan_text IS NULL THEN '' ELSE '<h2>Kế hoạch</h2><p>' || pg_temp.esc(plan_text) || '</p>' END
        || CASE WHEN issues_text IS NULL THEN '' ELSE '<h2>Vướng mắc</h2><p>' || pg_temp.esc(issues_text) || '</p>' END;

ALTER TABLE report_post ALTER COLUMN body SET NOT NULL;
ALTER TABLE report_post DROP COLUMN done_text;
ALTER TABLE report_post DROP COLUMN plan_text;
ALTER TABLE report_post DROP COLUMN issues_text;
