# Card suit templates

The four `.pgm` files in this directory are tiny starter glyph templates so
the app can build without downloading assets. For useful matching, replace
them with tightly cropped grayscale images from the exact card UI being
captured:

- `hearts.pgm`
- `diamonds.pgm`
- `spades.pgm`
- `clubs.pgm`

Keep the crop focused on the suit glyph and include a small, consistent border.
The detector uses OpenCV normalized template matching and accepts a match at
0.84 or higher. If the target app changes scale, capture templates at the
same screen density or add a scale-aware detector for that layout.