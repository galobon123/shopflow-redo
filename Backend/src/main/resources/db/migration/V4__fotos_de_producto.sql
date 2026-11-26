-- HU-11 · Every photo of a product, in order. position 0 is the cover the catalog card shows.

CREATE TABLE product_images (
    product_id  BIGINT       NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    url         VARCHAR(500) NOT NULL,
    position    INTEGER      NOT NULL CHECK (position >= 0),
    PRIMARY KEY (product_id, position)
);

-- The single photo each product had becomes its cover; product_images is now the only place for photos.
INSERT INTO product_images (product_id, url, position)
SELECT id, image_url, 0 FROM products WHERE image_url IS NOT NULL AND image_url <> '';

ALTER TABLE products DROP COLUMN image_url;
