-- Allow user_id to be NULL in firebase_tokens_v2 so we can clear it on logout
ALTER TABLE firebase_tokens_v2
    ALTER COLUMN user_id DROP NOT NULL;
