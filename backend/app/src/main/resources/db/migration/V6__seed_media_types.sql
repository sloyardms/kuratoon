-- Seed media types (docs/spec/01-data-model.md). Fixed UUIDv7 ids so they are stable across installs.
INSERT INTO media_type (id, name, slug, color, sort_order) VALUES
    ('01a15850-2d3f-7cd4-9f7e-58433cd2334d', 'Manga',     'manga',     '#E11D48', 0),
    ('01a15850-2d40-762e-9cde-cbc2119a3323', 'Manhwa',    'manhwa',    '#2563EB', 1),
    ('01a15850-2d41-761b-93b0-218c24b90e10', 'Manhua',    'manhua',    '#DC2626', 2),
    ('01a15850-2d42-70f1-aa17-9ec5cf2535a1', 'Comic',     'comic',     '#F59E0B', 3),
    ('01a15850-2d43-7223-84b7-451f91eeca18', 'Webtoon',   'webtoon',   '#10B981', 4),
    ('01a15850-2d44-7216-a287-0d9d0559d4a3', 'Doujinshi', 'doujinshi', '#A855F7', 5),
    ('01a15850-2d45-7bcc-a483-94da2f2b829a', 'One-shot',  'one-shot',  '#64748B', 6);
