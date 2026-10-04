package net.mage.cubewheel.wheel.edit;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.Function;
import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.config.WheelNode;
import net.mage.cubewheel.wheel.Icons;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Edits one wheel entry's label, icon, command (command entries and arc entries) and "fan out as arc" (rings and live
 * rings). Used for Edit and for the new-entry flows. Done hands the fields to the caller, which applies them through
 * {@link WheelEdits}: no error returns to the parent, an error stays here and shows in red. Cancel/Esc changes nothing.
 */
public final class NodeEditScreen extends Screen {
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GREY = 0xFFAAAAAA;
	private static final int RED = 0xFFFF5555;
	private static final int STEP = 36; // caption + box per field

	/** What the player typed; {@code command} is ignored where there is no command box, {@code asArc} without the toggle. */
	public record Fields(String label, String icon, String command, boolean asArc) {}

	private final Screen parent;
	private final Row.Kind kind;
	private final Function<Fields, String> done;
	private String label;
	private String icon;
	private String command;
	private boolean asArc;
	private String error;
	private EditBox labelBox;
	private EditBox iconBox;
	private EditBox commandBox;
	private Button fanButton;

	/**
	 * {@code kind} picks the boxes shown; {@code initial} fills them (null: blank); {@code done} applies the fields and
	 * returns null on success or the error to show.
	 */
	public NodeEditScreen(Screen parent, String title, Row.Kind kind, WheelNode initial, Function<Fields, String> done) {
		super(Component.literal(title));
		this.parent = parent;
		this.kind = kind;
		this.done = done;
		this.label = initial == null || initial.label == null ? "" : initial.label;
		this.icon = initial == null || initial.icon == null ? "" : initial.icon;
		this.command = initial == null || initial.command == null ? "" : initial.command;
		this.asArc = initial != null && initial.asArc;
	}

	private boolean hasCommand() {
		return kind == Row.Kind.LEAF || kind == Row.Kind.ARC_ENTRY;
	}

	private boolean hasFanOut() {
		return kind == Row.Kind.RING || kind == Row.Kind.LIVE_RING;
	}

	@Override
	protected void init() {
		keepValues(); // a resize re-runs init: carry over what was typed
		int w = boxWidth();
		int x = (width - w) / 2;
		int y = top();
		labelBox = box(x, y + 10, w, label, "Label");
		y += STEP;
		iconBox = box(x, y + 10, w - 22, icon, "Icon");
		iconBox.setHint(Component.literal("minecraft:ender_chest").withStyle(ChatFormatting.DARK_GRAY));
		y += STEP + 10; // room for the unknown-item hint
		commandBox = null;
		fanButton = null;
		if (hasCommand()) {
			commandBox = box(x, y + 10, w, command, "Command");
			commandBox.setHint(Component.literal("/spawn").withStyle(ChatFormatting.DARK_GRAY));
		}
		if (hasFanOut()) {
			fanButton = addRenderableWidget(Button.builder(fanText(), b -> {
				asArc = !asArc;
				b.setMessage(fanText());
			}).bounds(x, y + 10, w, 20).build());
		}
		int half = (w - 4) / 2;
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> finish()).bounds(x, height - 28, half, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(x + half + 4, height - 28, half, 20).build());
		setInitialFocus(labelBox);
	}

	private EditBox box(int x, int y, int w, String value, String name) {
		EditBox b = new EditBox(font, x, y, w, 18, Component.literal(name));
		b.setMaxLength(256);
		b.setValue(value);
		return addRenderableWidget(b);
	}

	private Component fanText() {
		return Component.literal("Fan out as arc: " + (asArc ? "ON" : "OFF"));
	}

	private void keepValues() {
		if (labelBox != null) label = labelBox.getValue();
		if (iconBox != null) icon = iconBox.getValue();
		if (commandBox != null) command = commandBox.getValue();
	}

	private void finish() {
		try {
			keepValues();
			error = done.apply(new Fields(label, icon.trim(), command, asArc));
			if (error == null) minecraft.gui.setScreen(parent);
		} catch (RuntimeException e) {
			CubeWheelClient.LOG.error("[cubewheel] wheel entry edit failed", e);
			error = "Could not apply: " + e.getMessage();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
		super.extractRenderState(g, mouseX, mouseY, partial);
		g.centeredText(font, title, width / 2, 8, WHITE);
		int w = boxWidth();
		int x = (width - w) / 2;
		int y = top();
		g.text(font, "Label", x, y, GREY);
		y += STEP;
		g.text(font, "Icon (an item id; blank for none)", x, y, GREY);
		String id = iconBox.getValue().trim();
		ItemStack stack = Icons.stack(id);
		if (!stack.isEmpty()) g.item(stack, x + w - 18, y + 11);
		else if (!id.isEmpty()) g.text(font, "Unknown item id: no icon will show", x, y + 30, RED);
		y += STEP + 10;
		if (commandBox != null) g.text(font, "Command (a server command, or cubewheel:<action>)", x, y, GREY);
		if (error != null) g.centeredText(font, font.plainSubstrByWidth(error, width - 20), width / 2, height - 42, RED);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
			finish();
			return true;
		}
		return super.keyPressed(event);
	}

	/** Esc / Cancel: back to the editor, changing nothing. */
	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int boxWidth() {
		return Math.min(280, width - 40);
	}

	private int top() {
		return 28;
	}
}
