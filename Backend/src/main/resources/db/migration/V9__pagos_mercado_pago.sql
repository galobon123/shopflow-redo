-- HU-18 · Paying with Mercado Pago (Checkout Pro).
-- The checkout link of the order's preference: one per order, reused on every attempt until the order expires.
ALTER TABLE orders ADD COLUMN mp_checkout_url TEXT;
-- The Mercado Pago payment that paid the order: a refund of that one cancels it, one of another payment doesn't.
ALTER TABLE orders ADD COLUMN mp_payment_id VARCHAR(40);
-- Why someone has to look at the order's payment (an approved payment for a cancelled order, a second payment,
-- an amount that doesn't match…). NULL: nothing to look at.
ALTER TABLE orders ADD COLUMN payment_issue VARCHAR(300);

-- The expiry job looks only at the orders still waiting for their payment.
CREATE INDEX idx_orders_pending_expiry ON orders (expires_at) WHERE status = 'PENDIENTE_PAGO';
