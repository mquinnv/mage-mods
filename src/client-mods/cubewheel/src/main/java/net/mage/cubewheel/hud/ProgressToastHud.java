package net.mage.cubewheel.hud;

import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.tracker.ProgressLabel;
import net.mage.cubewheel.tracker.TrackerStore;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Minecraft adapter for {@link ProgressToast}: listens to the tracker store's local counts (see
 * {@link TrackerStore#setProgressListener}), names them as the panels do ({@link ProgressLabel}), takes one-off
 * notices ({@link #notice}, from {@link NoticeWatcher}) and draws the popup's
 * lines centred a little below the crosshair, stacked downwards, each on its own dark plate and fading on its own.
 * Hidden while a screen is open or {@code toast.enabled} is off.
 */
public final class ProgressToastHud implements HudElement {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(CubeWheelClient.MOD_ID, "progress_toast");
	/** How far below the screen centre (the crosshair) the text's top sits. */
	private static final int BELOW_CROSSHAIR = 12;
	private static final int PAD = 2;
	/** The plate's alpha at full visibility (as {@link PanelsHud}'s backdrop). */
	private static final int PLATE_ALPHA = 0x80;

	private static final ProgressToast TOAST = new ProgressToast();
	private static boolean failed;

	private ProgressToastHud() {}

	/** Attaches the element after CubeWheel's panels and listens to the tracker store; call after both exist. */
	public static void register() {
		HudElementRegistry.attachElementAfter(Identifier.fromNamespaceAndPath(CubeWheelClient.MOD_ID, "panels"), ID,
				new ProgressToastHud());
		TrackerStore store = CubeWheelClient.tracker();
		if (store != null) store.setProgressListener(ProgressToastHud::onProgress);
	}

	/** A one-off notice for the popup ("Inventory full"), in {@code color}; ignored while {@code toast.enabled} is off. */
	public static void notice(String text, int color, long now) {
		TOAST.onNotice(text, color, now, CubeWheelClient.config().current().toast);
	}

	/** The store counted ({@code units} &gt; 0) or took back ({@code units} &lt; 0) progress for {@code key}. */
	private static void onProgress(String key, long units) {
		CubeWheelConfig.Toast cfg = CubeWheelClient.config().current().toast;
		if (cfg == null || !cfg.enabled) return;
		CubeWheelConfig.Tracker tracker = CubeWheelClient.config().current().tracker;
		Optional<ProgressLabel> label = ProgressLabel.of(CubeWheelClient.tracker(), key, tracker.local.worlds);
		if (label.isEmpty()) return;
		ProgressLabel l = label.get();
		if (units > 0) TOAST.onIncrement(key, l.name(), units, l.count(), l.done(), System.currentTimeMillis(), cfg);
		else TOAST.onReverse(key, -units, l.count(), l.done());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (failed) return;
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null || mc.gui.screen() != null) return;
			List<ProgressToast.Shown> shown = TOAST.lines(System.currentTimeMillis(), CubeWheelClient.config().current().toast);
			int y = g.guiHeight() / 2 + BELOW_CROSSHAIR;
			for (ProgressToast.Shown s : shown) {
				draw(g, mc.font, s, y);
				y += mc.font.lineHeight + 2 * PAD;
			}
		} catch (RuntimeException e) {
			CubeWheelClient.LOG.error("[cubewheel] progress popup failed", e);
			failed = true;
		}
	}

	private static void draw(GuiGraphicsExtractor g, Font font, ProgressToast.Shown s, int y) {
		int alpha = (int) Math.round(255 * Math.max(0, Math.min(1, s.alpha())));
		// The last few frames of the fade are skipped: (near-)zero text alpha has been drawn opaque by some font paths.
		if (alpha < 4) return;
		int w = font.width(s.text());
		int x = (g.guiWidth() - w) / 2;
		int plate = (int) Math.round(PLATE_ALPHA * alpha / 255.0);
		g.fill(x - PAD, y - PAD, x + w + PAD, y + font.lineHeight + PAD - 1, plate << 24);
		g.text(font, s.text(), x, y, alpha << 24 | (s.color() & 0xFFFFFF));
	}
}
