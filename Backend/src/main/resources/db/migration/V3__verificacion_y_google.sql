-- HU-31 · Email verification, HU-32 · Sign-in with Google.

ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Accounts that existed before verification keep buying as before.
UPDATE users SET email_verified = TRUE;

-- Google's stable id for the person ("sub"); the email can change on Google's side, this can't.
ALTER TABLE users ADD COLUMN google_sub VARCHAR(255) UNIQUE;

-- An account created with Google has no password, but every account needs some way to sign in.
ALTER TABLE users ALTER COLUMN password DROP NOT NULL;
ALTER TABLE users ADD CONSTRAINT users_can_sign_in CHECK (password IS NOT NULL OR google_sub IS NOT NULL);
