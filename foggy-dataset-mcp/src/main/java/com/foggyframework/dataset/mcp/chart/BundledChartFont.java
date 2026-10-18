package com.foggyframework.dataset.mcp.chart;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.io.InputStream;

/** A redistributable static Chinese font; never depends on host font installation. */
final class BundledChartFont {
    private BundledChartFont() { }

    private static final class Holder {
        private static final Font FONT = load();
    }

    static Font regular(float size) {
        return Holder.FONT.deriveFont(Font.PLAIN, size);
    }

    static Font bold(float size) {
        return Holder.FONT.deriveFont(Font.BOLD, size);
    }

    static void validateGlyphs(String text) {
        if (Holder.FONT.canDisplayUpTo(text.replace("\n", "").replace("\r", "")) != -1) {
            throw new IllegalArgumentException("图片文字包含内置字体不支持的字符，请使用简体中文或常见字母数字");
        }
    }

    private static Font load() {
        try (InputStream input = BundledChartFont.class.getResourceAsStream("/fonts/NotoSansSC-Regular.otf")) {
            if (input == null) {
                throw new IllegalStateException("图片渲染所需的内置字体资源缺失");
            }
            return Font.createFont(Font.TRUETYPE_FONT, input);
        } catch (IOException | FontFormatException exception) {
            throw new IllegalStateException("图片渲染所需的内置字体无法加载", exception);
        }
    }
}
