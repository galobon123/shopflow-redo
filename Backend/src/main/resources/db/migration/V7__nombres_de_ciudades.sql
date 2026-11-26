-- HU-16 · City names as people write them, for the province → city selects.
-- The V2 source left a soft hyphen (U+00AD) after many "í" (and sometimes an extra or missing accent):
-- "Rí­o Tercero" sorted and searched apart from "Río Tercero". No resulting name clashes with an existing one.
UPDATE city SET name = replace(name, 'íi' || chr(173), 'í') WHERE name LIKE '%íi' || chr(173) || '%';
UPDATE city SET name = replace(name, 'í' || chr(173), 'í') WHERE name LIKE '%í' || chr(173) || '%';
UPDATE city SET name = replace(name, 'i' || chr(173), 'í') WHERE name LIKE '%i' || chr(173) || '%';

UPDATE city SET name = 'Banderaló' WHERE state_id = 6 AND name = 'Banderaó';
UPDATE city SET name = 'Villa Concepción del Tío' WHERE state_id = 14 AND name = 'Villa Concepcón del Tío';
