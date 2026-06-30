package com.katariastoneworld.apis.service.audit;

import java.awt.Color;

/** Corporate color palette and layout constants for audit PDFs. */
public final class AuditReportTheme {

    private AuditReportTheme() {}

    public static final Color PRIMARY = color(0x0B, 0x3D, 0x91);
    public static final Color PRIMARY_DARK = color(0x06, 0x2A, 0x66);
    public static final Color TEAL = color(0x0D, 0x94, 0x88);
    public static final Color TEAL_LIGHT = color(0xE6, 0xF4, 0xF3);
    public static final Color GOLD = color(0xC9, 0xA2, 0x27);
    public static final Color GOLD_LIGHT = color(0xFB, 0xF6, 0xE8);
    public static final Color SUCCESS = color(0x16, 0xA3, 0x4A);
    public static final Color WARNING = color(0xEA, 0x58, 0x0C);
    public static final Color ERROR = color(0xDC, 0x26, 0x26);
    public static final Color NEUTRAL_BG = color(0xF4, 0xF6, 0xF9);
    public static final Color NEUTRAL_BORDER = color(0xE2, 0xE8, 0xF0);
    public static final Color TEXT_PRIMARY = color(0x0F, 0x17, 0x2A);
    public static final Color TEXT_MUTED = color(0x64, 0x74, 0x8B);
    public static final Color TABLE_HEADER = color(0x0B, 0x3D, 0x91);
    public static final Color TABLE_ROW_ALT = color(0xF8, 0xFA, 0xFC);
    public static final Color TABLE_ROW_WARN = color(0xFF, 0xF7, 0xED);
    public static final Color TABLE_ROW_ERROR = color(0xFE, 0xF2, 0xF2);
    public static final Color WHITE = Color.WHITE;

    public static final float MARGIN = 42f;
    public static final float HEADER_H = 34f;
    public static final float FOOTER_H = 26f;
    public static final float ROW_H = 14f;
    public static final float FONT_TABLE = 7.5f;
    public static final float FONT_HEADER = 8f;
    public static final float KPI_CARD_H = 52f;
    public static final float KPI_GAP = 10f;
    public static final float SECTION_GAP = 18f;

    private static Color color(int r, int g, int b) {
        return new Color(r, g, b);
    }
}
