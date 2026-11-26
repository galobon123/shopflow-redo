-- HU-16 · Deleting an address hides it from "Mis direcciones" but keeps the row,
-- so the orders that were sent there still show it (orders.address_id stays valid).
ALTER TABLE address ADD COLUMN deleted_at TIMESTAMP(6);

-- "Mis direcciones": the customer's live addresses.
CREATE INDEX idx_address_created_by ON address (created_by) WHERE deleted_at IS NULL;
