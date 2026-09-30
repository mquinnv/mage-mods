package com.mage.cubewheel.tracker.local;

import java.util.Locale;
import java.util.Map;

/** English singulars for objective nouns, block ids and mob names. Pure: no Minecraft/Fabric imports. */
public final class Singular {
	private static final Map<String, String> IRREGULAR = Map.of(
			"endermen", "enderman",
			"wolves", "wolf",
			"fish", "fish",
			"sheep", "sheep",
			"potatoes", "potato",
			"tomatoes", "tomato",
			"men", "man",
			"leaves", "leaf",
			"zombies", "zombie");

	private Singular() {}

	/** One word, lower-cased and singularised: "Wolves" -> "wolf", "berries" -> "berry", "glass" stays. */
	public static String word(String w) {
		if (w == null) return "";
		String s = w.trim().toLowerCase(Locale.ROOT);
		String irregular = IRREGULAR.get(s);
		if (irregular != null) return irregular;
		if (s.length() > 3 && s.endsWith("ies")) return s.substring(0, s.length() - 3) + "y";
		if (s.length() > 3 && s.endsWith("ves")) return s.substring(0, s.length() - 3) + "f";
		// "blazes", "horses": a single s/z keeps its e; "glasses", "buzzes" drop "es" below.
		if (s.matches(".*[^sz][sz]es")) return s.substring(0, s.length() - 1);
		if (s.matches(".*(s|x|z|ch|sh)es")) return s.substring(0, s.length() - 2);
		if (s.length() > 1 && s.endsWith("s") && !s.endsWith("ss")) return s.substring(0, s.length() - 1);
		return s;
	}

	/**
	 * A phrase with only its last word singularised; lower case, "_" read as a space, whitespace collapsed:
	 * "Mana  Wolves" -> "mana wolf", "sweet_berry_bush" -> "sweet berry bush".
	 */
	public static String phrase(String p) {
		if (p == null) return "";
		String s = p.replace('_', ' ').trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
		if (s.isEmpty()) return s;
		int space = s.lastIndexOf(' ');
		return space < 0 ? word(s) : s.substring(0, space + 1) + word(s.substring(space + 1));
	}
}
