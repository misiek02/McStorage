package pl.misiek.copperpipes.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import pl.misiek.copperpipes.card.CardFilter;
import pl.misiek.copperpipes.card.SortingCard;
import pl.misiek.copperpipes.item.SortingCardItem;
import pl.misiek.copperpipes.menu.CardEditMenu;
import pl.misiek.copperpipes.packet.UpdateCardPayload;

/**
 * Card editor: rule list, a text field with tag / item / mod autocomplete, a ghost slot whose item's id, mod and
 * tags are offered as suggestions, priority and whitelist/blacklist. Changes are sent to the server on close.
 */
public class CardEditScreen extends AbstractContainerScreen<CardEditMenu> {
	private static final int WIDTH = 176;
	private static final int HEIGHT = 240;
	private static final int LIST_X = 8;
	private static final int LIST_Y = 18;
	private static final int ROW_HEIGHT = 12;
	private static final int VISIBLE_ROWS = 6;
	private static final int FIELD_Y = 95;
	private static final int PRIORITY_Y = 133;
	private static final int MAX_SUGGESTIONS = 7;
	private static final int SUGGESTION_HEIGHT = 11;

	private static final int PANEL = 0xFFC6C6C6;
	private static final int PANEL_LIGHT = 0xFFFFFFFF;
	private static final int PANEL_DARK = 0xFF555555;
	private static final int INSET = 0xFF8B8B8B;
	private static final int TEXT = 0xFF404040;
	private static final int TEXT_VALID = 0xFF1F3F7F;
	private static final int TEXT_INVALID = 0xFFB02020;

	private final List<String> rules;
	private int priority;
	private boolean blacklist;
	private final SortingCard original;

	private EditBox field;
	private int scroll;
	private List<String> suggestions = List.of();
	private int selectedSuggestion;
	private ItemStack lastGhost = ItemStack.EMPTY;

	private static List<String> tagIds;
	private static List<String> itemIds;
	private static List<String> modIds;

	public CardEditScreen(CardEditMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		InteractionHand hand = CardEditMenu.cardHand(inventory.player);
		this.original = hand == null ? SortingCard.EMPTY : SortingCardItem.cardOf(inventory.player.getItemInHand(hand));
		this.rules = new ArrayList<>(this.original.rules());
		this.priority = this.original.priority();
		this.blacklist = this.original.blacklist();
		this.inventoryLabelY = CardEditMenu.INVENTORY_TOP - 11;
	}

	@Override
	protected void init() {
		super.init();
		loadRegistryNames();
		int x = this.leftPos;
		int y = this.topPos;

		this.field = new EditBox(this.font, x + 10, y + FIELD_Y + 2, 124, 10, Component.translatable("gui.copperpipes.card.rule"));
		this.field.setBordered(false);
		this.field.setMaxLength(UpdateCardPayload.MAX_RULE_LENGTH);
		this.field.setHint(Component.translatable("gui.copperpipes.card.rule_hint"));
		this.field.setResponder(text -> {
			this.field.setTextColor(text.isBlank() || CardFilter.Rule.parse(text).isPresent() ? 0xFFFFFFFF : 0xFFFF6060);
			updateSuggestions();
		});
		this.addRenderableWidget(this.field);

		this.addRenderableWidget(Button.builder(Component.literal("+"), b -> addRule(this.field.getValue()))
				.bounds(x + 140, y + FIELD_Y - 1, 28, 14).build());
		this.addRenderableWidget(Button.builder(Component.literal("-"), b -> this.priority = Math.max(0, this.priority - 1))
				.bounds(x + 8, y + PRIORITY_Y, 14, 14).build());
		this.addRenderableWidget(Button.builder(Component.literal("+"), b -> this.priority = Math.min(SortingCard.MAX_PRIORITY, this.priority + 1))
				.bounds(x + 84, y + PRIORITY_Y, 14, 14).build());
		this.addRenderableWidget(Button.builder(modeLabel(), b -> {
			this.blacklist = !this.blacklist;
			b.setMessage(modeLabel());
		}).bounds(x + 102, y + PRIORITY_Y, 66, 14).build());

		this.setInitialFocus(this.field);
	}

	private Component modeLabel() {
		return Component.translatable(this.blacklist ? "gui.copperpipes.card.blacklist" : "gui.copperpipes.card.whitelist");
	}

	/** Tag, item and mod names for autocomplete; read once (tags come from the server's synced data). */
	private static void loadRegistryNames() {
		if (tagIds != null) {
			return;
		}
		tagIds = BuiltInRegistries.ITEM.getTags().map(named -> named.key().location().toString()).sorted().toList();
		TreeSet<String> items = new TreeSet<>();
		TreeSet<String> mods = new TreeSet<>();
		for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
			items.add(id.toString());
			mods.add(id.getNamespace());
		}
		itemIds = List.copyOf(items);
		modIds = List.copyOf(mods);
	}

	/** Tags change on datapack reload; forget the cached names when the player leaves a world. */
	public static void clearRegistryNames() {
		tagIds = null;
	}

	// ---------------------------------------------------------------- rules

	private void addRule(String text) {
		String rule = text.trim();
		if (rule.isEmpty() || CardFilter.Rule.parse(rule).isEmpty() || this.rules.contains(rule)
				|| this.rules.size() >= SortingCard.MAX_RULES) {
			return;
		}
		this.rules.add(rule);
		this.field.setValue("");
		this.scroll = Math.max(0, this.rules.size() - VISIBLE_ROWS);
	}

	private void updateSuggestions() {
		String text = this.field.getValue().trim();
		String negation = text.startsWith("!") ? "!" : "";
		String body = negation.isEmpty() ? text : text.substring(1).trim();
		List<String> result = new ArrayList<>();

		if (body.isEmpty()) {
			ItemStack ghost = this.menu.ghostItem();
			if (!ghost.isEmpty()) {
				Identifier id = BuiltInRegistries.ITEM.getKey(ghost.getItem());
				result.add(id.toString());
				result.add("@" + id.getNamespace());
				ghost.typeHolder().tags().map(tag -> "#" + tag.location()).sorted().forEach(result::add);
			}
		} else if (body.startsWith("#")) {
			match(tagIds, body.substring(1), "#", result);
		} else if (body.startsWith("@")) {
			match(modIds, body.substring(1), "@", result);
		} else {
			match(itemIds, body, "", result);
		}

		this.suggestions = result.stream()
				.map(s -> negation + s)
				.filter(s -> !s.equals(text) && !this.rules.contains(s))
				.limit(MAX_SUGGESTIONS)
				.toList();
		this.selectedSuggestion = 0;
	}

	/** Names starting with the query first, then names merely containing it. */
	private static void match(List<String> names, String query, String prefix, List<String> out) {
		String q = query.toLowerCase();
		names.stream()
				.filter(name -> name.contains(q))
				.sorted(Comparator.comparing((String name) -> !(name.startsWith(q) || name.contains(":" + q))).thenComparing(String::length))
				.limit(MAX_SUGGESTIONS * 2L)
				.forEach(name -> out.add(prefix + name));
	}

	private void acceptSuggestion(int index) {
		if (index >= 0 && index < this.suggestions.size()) {
			this.field.setValue(this.suggestions.get(index));
			this.field.moveCursorToEnd(false);
		}
	}

	private boolean suggestionsVisible() {
		return this.field.isFocused() && !this.suggestions.isEmpty();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		// A new item in the ghost slot: offer its id, mod and tags right away.
		ItemStack ghost = this.menu.ghostItem();
		if (!ItemStack.matches(ghost, this.lastGhost)) {
			this.lastGhost = ghost.copy();
			this.field.setValue("");
			this.setFocused(this.field);
			this.field.setFocused(true);
			updateSuggestions();
		}
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.field.isFocused() && !event.isEscape()) {
			if (suggestionsVisible() && event.isCycleFocus()) {
				acceptSuggestion(this.selectedSuggestion);
				return true;
			}
			if (suggestionsVisible() && event.isDown()) {
				this.selectedSuggestion = (this.selectedSuggestion + 1) % this.suggestions.size();
				return true;
			}
			if (suggestionsVisible() && event.isUp()) {
				this.selectedSuggestion = (this.selectedSuggestion + this.suggestions.size() - 1) % this.suggestions.size();
				return true;
			}
			if (event.isConfirmation()) {
				addRule(this.field.getValue());
				return true;
			}
			// Keep typed letters (e.g. the inventory key) in the text field.
			this.field.keyPressed(event);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int mx = (int) event.x() - this.leftPos;
		int my = (int) event.y() - this.topPos;

		if (suggestionsVisible()) {
			int top = suggestionsTop();
			if (mx >= LIST_X && mx < WIDTH - 8 && my >= top && my < FIELD_Y - 1) {
				acceptSuggestion((my - top) / SUGGESTION_HEIGHT);
				return true;
			}
		}

		// The remove "x" at the end of each visible rule row.
		for (int row = 0; row < VISIBLE_ROWS; row++) {
			int index = this.scroll + row;
			int rowY = LIST_Y + row * ROW_HEIGHT;
			if (index < this.rules.size() && mx >= WIDTH - 20 && mx < WIDTH - 9 && my >= rowY && my < rowY + ROW_HEIGHT) {
				this.rules.remove(index);
				this.scroll = Math.max(0, Math.min(this.scroll, this.rules.size() - VISIBLE_ROWS));
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		int mx = (int) x - this.leftPos;
		int my = (int) y - this.topPos;
		if (mx >= LIST_X && mx < WIDTH - 8 && my >= LIST_Y && my < LIST_Y + VISIBLE_ROWS * ROW_HEIGHT) {
			int maxScroll = Math.max(0, this.rules.size() - VISIBLE_ROWS);
			this.scroll = Math.clamp(this.scroll - (int) Math.signum(scrollY), 0, maxScroll);
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public void removed() {
		SortingCard edited = new SortingCard(this.rules, this.priority, this.blacklist);
		if (!edited.equals(this.original)) {
			ClientPlayNetworking.send(new UpdateCardPayload(edited));
		}
		super.removed();
	}

	// ---------------------------------------------------------------- drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		int x = this.leftPos;
		int y = this.topPos;
		panel(graphics, x, y, WIDTH, HEIGHT);
		inset(graphics, x + LIST_X - 1, y + LIST_Y - 1, WIDTH - 14, VISIBLE_ROWS * ROW_HEIGHT + 2, 0xFFA0A0A0);
		inset(graphics, x + 7, y + FIELD_Y - 1, 130, 14, 0xFF000000);
		slot(graphics, x + CardEditMenu.GHOST_X - 1, y + CardEditMenu.GHOST_Y - 1);
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				slot(graphics, x + 7 + col * 18, y + CardEditMenu.INVENTORY_TOP - 1 + row * 18);
			}
		}
		for (int col = 0; col < 9; col++) {
			slot(graphics, x + 7 + col * 18, y + CardEditMenu.INVENTORY_TOP + 57);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
		super.extractLabels(graphics, xm, ym);
		int mx = xm - this.leftPos;
		int my = ym - this.topPos;

		if (this.rules.isEmpty()) {
			graphics.text(this.font, Component.translatable("gui.copperpipes.card.no_rules"), LIST_X + 3, LIST_Y + 3, 0xFF606060, false);
		}
		for (int row = 0; row < VISIBLE_ROWS && this.scroll + row < this.rules.size(); row++) {
			String rule = this.rules.get(this.scroll + row);
			int rowY = LIST_Y + row * ROW_HEIGHT;
			boolean valid = CardFilter.Rule.parse(rule).isPresent();
			graphics.text(this.font, this.font.plainSubstrByWidth(rule, WIDTH - 36), LIST_X + 3, rowY + 2, valid ? TEXT_VALID : TEXT_INVALID, false);
			boolean hover = mx >= WIDTH - 20 && mx < WIDTH - 9 && my >= rowY && my < rowY + ROW_HEIGHT;
			graphics.text(this.font, "x", WIDTH - 17, rowY + 2, hover ? 0xFFFF4040 : 0xFF707070, false);
		}
		if (this.rules.size() > VISIBLE_ROWS) {
			String more = (this.scroll + 1) + "-" + Math.min(this.rules.size(), this.scroll + VISIBLE_ROWS) + "/" + this.rules.size();
			graphics.text(this.font, more, WIDTH - 8 - this.font.width(more), 6, 0xFF606060, false);
		}

		ItemStack ghost = this.menu.ghostItem();
		Component ghostText = ghost.isEmpty()
				? Component.translatable("gui.copperpipes.card.ghost_hint")
				: ghost.getHoverName();
		graphics.text(this.font, this.font.plainSubstrByWidth(ghostText.getString(), WIDTH - 36), 30, CardEditMenu.GHOST_Y + 5, TEXT, false);
		graphics.text(this.font, Component.translatable("gui.copperpipes.card.priority", this.priority), 26, PRIORITY_Y + 3, TEXT, false);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		if (suggestionsVisible()) {
			graphics.nextStratum();
			int x = this.leftPos;
			int top = this.topPos + suggestionsTop();
			graphics.fill(x + LIST_X - 1, top - 1, x + WIDTH - 7, this.topPos + FIELD_Y - 1, 0xF0101010);
			for (int i = 0; i < this.suggestions.size(); i++) {
				int lineY = top + i * SUGGESTION_HEIGHT;
				if (i == this.selectedSuggestion) {
					graphics.fill(x + LIST_X, lineY, x + WIDTH - 8, lineY + SUGGESTION_HEIGHT, 0xFF3A3A6A);
				}
				graphics.text(this.font, this.font.plainSubstrByWidth(this.suggestions.get(i), WIDTH - 20), x + LIST_X + 2, lineY + 2,
						i == this.selectedSuggestion ? 0xFFFFFF80 : 0xFFE0E0E0, false);
			}
		}
	}

	private int suggestionsTop() {
		return FIELD_Y - 1 - this.suggestions.size() * SUGGESTION_HEIGHT;
	}

	private static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, PANEL_DARK);
		g.fill(x, y, x + w - 1, y + h - 1, PANEL_LIGHT);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL);
	}

	private static void inset(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill) {
		g.fill(x, y, x + w, y + h, PANEL_LIGHT);
		g.fill(x, y, x + w - 1, y + h - 1, PANEL_DARK);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
	}

	private static void slot(GuiGraphicsExtractor g, int x, int y) {
		inset(g, x, y, 18, 18, INSET);
	}
}
