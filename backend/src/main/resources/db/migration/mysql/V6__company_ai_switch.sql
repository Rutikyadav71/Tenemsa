-- Phase 6c: per-company AI on/off switch (on by default).
ALTER TABLE companies ADD COLUMN ai_enabled BOOLEAN NOT NULL DEFAULT TRUE;
