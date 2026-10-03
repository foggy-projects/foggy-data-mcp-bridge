---
doc_type: code-inventory
intended_for: developers
purpose: Record bundled chart font provenance and redistribution terms.
---

NotoSansSC-Regular.otf is an unmodified static, Simplified Chinese subset font
from the official Noto CJK project. It is loaded from the classpath, so image
export works without an installed system font or a font download at runtime.

- Upstream commit: `f8d157532fbfaeda587e826d4cd5b21a49186f7c`
- Source: https://github.com/notofonts/noto-cjk/blob/f8d157532fbfaeda587e826d4cd5b21a49186f7c/Sans/SubsetOTF/SC/NotoSansSC-Regular.otf
- License: SIL Open Font License 1.1, included verbatim as `OFL.txt`
- Embedded copyright metadata: © 2014-2021 Adobe (http://www.adobe.com/).
- Embedded version metadata: `Version 2.004;hotconv 1.0.118;makeotfexe 2.5.65603`
- File size: 8,331,336 bytes
- SHA-256: `faa6c9df652116dde789d351359f3d7e5d2285a2b2a1f04a2d7244df706d5ea9`

The font covers Simplified Chinese and common Latin text. Unsupported glyphs
(such as some emoji or rare characters) produce an explicit safe error instead
of unreadable missing-glyph boxes. Existing external ECharts service fonts are
not loaded by this renderer.
