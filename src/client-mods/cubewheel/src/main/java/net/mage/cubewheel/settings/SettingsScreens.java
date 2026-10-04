package net.mage.cubewheel.settings;

import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.ListOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.isxander.yacl3.api.utils.OptionUtils;
import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.config.ConfigStore;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.config.DefaultConfig;
import net.mage.cubewheel.settings.SettingsSpec.Category;
import net.mage.cubewheel.settings.SettingsSpec.Group;
import net.mage.cubewheel.settings.SettingsSpec.IntRange;
import net.mage.cubewheel.settings.SettingsSpec.Setting;
import net.mage.cubewheel.settings.SettingsSpec.Text;
import net.mage.cubewheel.settings.SettingsSpec.TextList;
import net.mage.cubewheel.settings.SettingsSpec.Toggle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * The YACL settings screen, built from {@link SettingsSpec}. Options edit a draft copy of the config; YACL's save
 * hands the draft to {@link ConfigStore#apply}, so nothing changes until the player saves.
 */
public final class SettingsScreens {
	private SettingsScreens() {}

	/** The config being edited. Replaced by a fresh copy after each save, so the screen never edits the live config. */
	private static final class Draft {
		CubeWheelConfig cfg;
		/** The built YACL config, so a save can re-sync its options; set before the screen exists. */
		YetAnotherConfigLib screen;

		Draft(CubeWheelConfig cfg) {
			this.cfg = cfg;
		}
	}

	/** The settings screen; {@code parent} is shown again when it closes (null = back to the game). */
	public static Screen root(Screen parent) {
		ConfigStore store = CubeWheelClient.config();
		Draft draft = new Draft(ConfigStore.copyOf(store.current()));
		YetAnotherConfigLib.Builder yacl = YetAnotherConfigLib.createBuilder()
				.title(Component.literal("CubeWheel settings"))
				.save(() -> save(store, draft));
		List<Category> categories = SettingsSpec.categories();
		for (int i = 0; i < categories.size(); i++) {
			Category category = categories.get(i);
			ConfigCategory.Builder tab = ConfigCategory.createBuilder().name(Component.literal(category.name()));
			if (i == 0 && !store.lastLoadOk()) {
				// Root-group options sit above every group, so this is the first thing on the General tab.
				tab.option(LabelOption.create(Component.literal(
						"cubewheel.json has an error and was not loaded. Saving here replaces it.").withStyle(ChatFormatting.RED)));
			}
			for (Group group : category.groups()) addGroup(tab, group, draft);
			if (i == 0) tab.group(tools(store, draft));
			yacl.category(tab.build());
		}
		draft.screen = yacl.build();
		return draft.screen.generateScreen(parent);
	}

	/**
	 * One spec group: its single-value settings in one YACL group, then each list setting as its own group after it
	 * (a YACL list option is a group itself and cannot sit inside another).
	 */
	private static void addGroup(ConfigCategory.Builder tab, Group group, Draft draft) {
		OptionGroup.Builder plain = OptionGroup.createBuilder().name(Component.literal(group.name()));
		List<ListOption<String>> lists = new ArrayList<>();
		int plainCount = 0;
		for (Setting setting : group.settings()) {
			if (setting instanceof TextList list) {
				lists.add(listOption(list, draft));
			} else {
				plain.option(option(setting, draft));
				plainCount++;
			}
		}
		if (plainCount > 0) tab.group(plain.build());
		for (ListOption<String> list : lists) tab.group(list);
	}

	/** A single-value setting as a YACL option reading and writing the draft. */
	private static Option<?> option(Setting setting, Draft draft) {
		return switch (setting) {
			case Toggle t -> Option.<Boolean>createBuilder()
					.name(Component.literal(t.label()))
					.description(describe(t))
					.binding(t.defaultValue(), () -> t.getter().apply(draft.cfg), v -> t.setter().accept(draft.cfg, v))
					.controller(TickBoxControllerBuilder::create)
					.build();
			case IntRange r -> Option.<Integer>createBuilder()
					.name(Component.literal(r.label()))
					.description(describe(r))
					.binding(r.defaultValue(), () -> r.getter().apply(draft.cfg), v -> r.setter().accept(draft.cfg, v))
					.controller(o -> IntegerSliderControllerBuilder.create(o).range(r.min(), r.max()).step(1))
					.build();
			case Text t -> Option.<String>createBuilder()
					.name(Component.literal(t.label()))
					.description(describe(t))
					.binding(t.defaultValue(), () -> nonNull(t.getter().apply(draft.cfg)), v -> t.setter().accept(draft.cfg, v))
					.controller(StringControllerBuilder::create)
					.build();
			case TextList l -> throw new IllegalArgumentException("list setting " + l.id() + " is a group, not an option");
		};
	}

	private static ListOption<String> listOption(TextList l, Draft draft) {
		return ListOption.<String>createBuilder()
				.name(Component.literal(l.label()))
				.description(describe(l))
				.binding(l.defaultValue(), () -> {
					List<String> v = l.getter().apply(draft.cfg);
					return v == null ? new ArrayList<>() : new ArrayList<>(v);
				}, v -> l.setter().accept(draft.cfg, v))
				.controller(StringControllerBuilder::create)
				.initial("")
				.build();
	}

	private static OptionDescription describe(Setting s) {
		return OptionDescription.of(Component.literal(s.tooltip()));
	}

	private static String nonNull(String s) {
		return s == null ? "" : s;
	}

	/** The General tab's extras: actions and the keybind hint. */
	private static OptionGroup tools(ConfigStore store, Draft draft) {
		return OptionGroup.createBuilder()
				.name(Component.literal("Tools"))
				.option(ButtonOption.createBuilder()
						.name(Component.literal("Reload from file"))
						.description(OptionDescription.of(Component.literal(
								"Re-read cubewheel.json, dropping unsaved changes here, and close this screen.")))
						.action((screen, button) -> {
							CubeWheelClient.reloadAndReport(Minecraft.getInstance());
							screen.onClose();
						})
						.build())
				.option(ButtonOption.createBuilder()
						.name(Component.literal("Open config folder"))
						.description(OptionDescription.of(Component.literal(
								"Open the folder with cubewheel.json and CubeWheel's other files.")))
						.action((screen, button) -> Util.getPlatform().openPath(FabricLoader.getInstance().getConfigDir()))
						.build())
				// A tick box, not a button: YACL only saves when an option has a pending change, so a button that
				// just edited the draft would be lost on "Done" unless something else had changed too.
				.option(Option.<Boolean>createBuilder()
						.name(Component.literal("Reset wheel to default"))
						.description(OptionDescription.of(Component.literal(
								"Tick and save to replace your whole wheel with the default one. Your current wheel is lost.")))
						.binding(false, () -> false, reset -> {
							if (reset) draft.cfg.wheel = DefaultConfig.wheel();
						})
						.controller(TickBoxControllerBuilder::create)
						.build())
				.option(LabelOption.create(Component.literal("Keybinds: Options › Controls › CubeWheel")))
				.build();
	}

	/** YACL's save: options have already written into the draft. */
	private static void save(ConfigStore store, Draft draft) {
		List<String> warnings;
		try {
			warnings = store.apply(draft.cfg);
		} catch (IOException e) {
			CubeWheelClient.LOG.error("[cubewheel] could not save settings", e);
			chat(Component.literal("[CubeWheel] could not save: " + e.getMessage()).withStyle(ChatFormatting.RED));
			return;
		}
		draft.cfg = ConfigStore.copyOf(store.current()); // the applied draft is live now; keep editing a copy
		resync(draft.screen);
		for (String w : warnings) chat(Component.literal("[CubeWheel] " + w).withStyle(ChatFormatting.YELLOW));
		chat(Component.literal("CubeWheel settings saved").withStyle(ChatFormatting.GREEN));
	}

	/**
	 * Points every option whose pending value differs from its binding back at the binding, i.e. at the normalised
	 * config. YACL forgets pending values only before it calls save, while the draft still held the player's input;
	 * a value the normaliser then changed (an invalid regex, a command given its "/") would otherwise stay "changed",
	 * so the button would stay "Save", "Done" would never close, and each press would re-save and repeat warnings.
	 */
	private static void resync(YetAnotherConfigLib screen) {
		OptionUtils.forEachOptions(screen, o -> {
			try {
				if (o.changed()) o.forgetPendingValue();
			} catch (RuntimeException e) {
				CubeWheelClient.LOG.error("[cubewheel] could not refresh setting {}", o.name().getString(), e);
			}
		});
	}

	/** A client chat line; logged instead when there is no player (screen opened from the title screen's Mod Menu). */
	private static void chat(Component message) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) mc.player.sendSystemMessage(message);
		else CubeWheelClient.LOG.info("[cubewheel] {}", message.getString());
	}
}
