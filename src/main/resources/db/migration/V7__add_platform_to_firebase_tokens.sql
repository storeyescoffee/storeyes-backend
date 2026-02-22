-- Add platform column to firebase_tokens (IOS, ANDROID)
ALTER TABLE firebase_tokens ADD COLUMN IF NOT EXISTS platform VARCHAR(20) NULL;

-- Set default for existing rows, then make NOT NULL
UPDATE firebase_tokens SET platform = 'ANDROID' WHERE platform IS NULL;
ALTER TABLE firebase_tokens ALTER COLUMN platform SET NOT NULL;
