package com.mage.cubewheel.sva;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Legacy formatting codes as ManaCube's API returns them: both {@code §} and {@code &} markers, colours
 * {@code 0-9a-f}, decorations {@code k-o}, reset {@code r}, Spigot hex {@code §x§R§R§G§G§B§B} and {@code &#RRGGBB}.
 * A {@code &} that does not start a code is literal text ("Bosses & Minibosses"). Pure: no Minecraft imports.
 */
public final class LegacyText {
	private LegacyText() {}

	/** A run of text with one style; {@code rgb} is -1 for "no colour set". */
	public record Span(String text, int rgb, boolean bold, boolean italic, boolean underlined,
			boolean strikethrough, boolean obfuscated) {}

	private static final int[] PALETTE = {
			0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
			0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

	/** The text without any codes; "" for null. */
	public static String strip(String s) {
		StringBuilder out = new StringBuilder();
		for (Span span : parse(s)) out.append(span.text());
		return out.toString();
	}

	/** Stripped, whitespace collapsed, trimmed, lower-cased: the form used for name look-ups. */
	public static String normalize(String s) {
		return strip(s).replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
	}

	public static List<Span> parse(String s) {
		List<Span> out = new ArrayList<>();
		if (s == null || s.isEmpty()) return out;
		StringBuilder text = new StringBuilder();
		int rgb = -1;
		boolean bold = false, italic = false, underlined = false, strike = false, obfuscated = false;
		int i = 0;
		int n = s.length();
		while (i < n) {
			char ch = s.charAt(i);
			if ((ch == '§' || ch == '&') && i + 1 < n) {
				char code = Character.toLowerCase(s.charAt(i + 1));
				int hex = -1;
				int consumed = 0;
				if (code == 'x') {
					hex = spigotHex(s, i, ch);
					consumed = hex >= 0 ? 14 : 0;
				} else if (code == '#') {
					hex = hashHex(s, i + 2);
					consumed = hex >= 0 ? 8 : 0;
				}
				int palette = Character.digit(code, 16);
				boolean isDecoration = "klmno".indexOf(code) >= 0;
				boolean known = consumed > 0 || palette >= 0 || isDecoration || code == 'r';
				if (known || ch == '§') {
					flush(out, text, rgb, bold, italic, underlined, strike, obfuscated);
					if (consumed > 0 || palette >= 0 || code == 'r') {
						rgb = consumed > 0 ? hex : palette >= 0 ? PALETTE[palette] : -1;
						bold = italic = underlined = strike = obfuscated = false;
					} else if (isDecoration) {
						switch (code) {
							case 'k' -> obfuscated = true;
							case 'l' -> bold = true;
							case 'm' -> strike = true;
							case 'n' -> underlined = true;
							default -> italic = true;
						}
					}
					// an unknown "§?" pair is dropped, as vanilla does
					i += consumed > 0 ? consumed : 2;
					continue;
				}
			}
			text.append(ch);
			i++;
		}
		flush(out, text, rgb, bold, italic, underlined, strike, obfuscated);
		return out;
	}

	private static void flush(List<Span> out, StringBuilder text, int rgb, boolean bold, boolean italic,
			boolean underlined, boolean strike, boolean obfuscated) {
		if (text.isEmpty()) return;
		out.add(new Span(text.toString(), rgb, bold, italic, underlined, strike, obfuscated));
		text.setLength(0);
	}

	/** "§x§R§R§G§G§B§B" starting at {@code at} (the marker may be § or &, but the same throughout). */
	private static int spigotHex(String s, int at, char marker) {
		if (at + 14 > s.length()) return -1;
		int v = 0;
		for (int k = 0; k < 6; k++) {
			int p = at + 2 + k * 2;
			if (s.charAt(p) != marker && s.charAt(p) != '§' && s.charAt(p) != '&') return -1;
			int d = Character.digit(s.charAt(p + 1), 16);
			if (d < 0) return -1;
			v = v * 16 + d;
		}
		return v;
	}

	/** "RRGGBB" starting at {@code at}. */
	private static int hashHex(String s, int at) {
		if (at + 6 > s.length()) return -1;
		int v = 0;
		for (int k = 0; k < 6; k++) {
			int d = Character.digit(s.charAt(at + k), 16);
			if (d < 0) return -1;
			v = v * 16 + d;
		}
		return v;
	}
}
