package pl.misiek.copperpipes.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import pl.misiek.copperpipes.card.CardFilter.Rule;
import pl.misiek.copperpipes.card.CardFilter.Rule.Kind;
import pl.misiek.copperpipes.card.SortingCard;
import pl.misiek.copperpipes.item.SortingCardItem;
import pl.misiek.copperpipes.menu.CardEditMenu;
import pl.misiek.copperpipes.packet.UpdateCardPayload;

/**
 * Card editor. A rule is built from three explicit parts: its kind (item / tag / mod, chosen with a button), a
 * name typed into the field (with autocomplete, checked against the game's registries) and whether it includes
 * or excludes (the "Add" and "Exclude" buttons). The ghost slot offers a clicked item's id, mod and tags.
 * Changes are sent to the server when the screen closes.
 */
public class CardEditScreen extends AbstractContainerScreen<CardEditMenu> {
	private static final int WIDTH = 176;
	private static final int HEIGHT = CardEditMenu.INVENTORY_TOP + 82;
	private static final int LIST_X = 8;
	private static final int LIST_Y = 18;
	private static final int ROW_HEIGHT = 12;
	private static final int VISIBLE_ROWS = 6;
	private static final int FIELD_Y = 95;
	private static final int BUTTONS_Y = 111;
	private static final int PRIORITY_Y = CardEditMenu.GHOST_Y + 21;
	private static final int MAX_SUGGESTIONS = 7;
	private static final int SUGGESTION_HEIGHT = 11;

	private static final int PANEL = 0xFFC6C6C6;
	private static final int PANEL_LIGHT = 0xFFFFFFFF;
	private static final int PANEL_DARK = 0xFF555555;
	private static final int INSET = 0xFF8B8B8B;
	private static final int TEXT = 0xFF404040;
	private static final int TEXT_ID = 0xFF202020;
	private static final int TEXT_EXCLUDED = 0xFFB02020;
	private static final int TEXT_MISSING = 0xFF9A2A2A;

	private final List<String> rules;
	private int priority;
	private boolean blacklist;
	private final SortingCard original;

	private Kind kind = Kind.ITEM;
	private EditBox field;
	private Button kindButton;
	private Button addButton;
	private Button excludeButton;
	private int scroll;
	private List<Suggestion> suggestions = List.of();
	private int selectedSuggestion;
	private ItemStack lastGhost = ItemStack.EMPTY;
	private boolean updatingField;

	private static List<String> tagIds;
	private static List<String> itemIds;
	private static List<String> modIds;
	private static Set<String> tagSet;
	private static Set<String> itemSet;
	private static Set<String> modSet;

	private record Suggestion(Kind kind, String id) {
	}

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

		this.kindButton = this.addRenderableWidget(Button.builder(kindLabel(), b -> setKind(this.kind.next()))
				.bounds(x + 7, y + FIELD_Y - 1, 52, 14).build());

		this.field = new EditBox(this.font, x + 64, y + FIELD_Y + 2, 102, 10, Component.translatable("gui.copperpipes.card.name"));
		this.field.setBordered(false);
		this.field.setMaxLength(UpdateCardPayload.MAX_RULE_LENGTH);
		this.addRenderableWidget(this.field);

		this.addButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.copperpipes.card.add"), b -> addRule(false))
				.bounds(x + 7, y + BUTTONS_Y, 80, 14).build());
		this.excludeButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.copperpipes.card.exclude"), b -> addRule(true))
				.bounds(x + 89, y + BUTTONS_Y, 80, 14).build());

		this.addRenderableWidget(Button.builder(Component.literal("-"), b -> this.priority = Math.max(0, this.priority - 1))
				.bounds(x + 7, y + PRIORITY_Y, 14, 14).build());
		this.addRenderableWidget(Button.builder(Component.literal("+"), b -> this.priority = Math.min(SortingCard.MAX_PRIORITY, this.priority + 1))
				.bounds(x + 84, y + PRIORITY_Y, 14, 14).build());
		this.addRenderableWidget(Button.builder(modeLabel(), b -> {
			this.blacklist = !this.blacklist;
			b.setMessage(modeLabel());
		}).bounds(x + 102, y + PRIORITY_Y, 67, 14).build());

		this.field.setResponder(this::onFieldChanged);
		this.setInitialFocus(this.field);
		onFieldChanged(this.field.getValue());
	}

	private Component kindLabel() {
		return Component.translatable(this.kind.translationKey());
	}

	private Component modeLabel() {
		return Component.translatable(this.blacklist ? "gui.copperpipes.card.blacklist" : "gui.copperpipes.card.whitelist");
	}

	private void setKind(Kind kind) {
		this.kind = kind;
		this.kindButton.setMessage(kindLabel());
		onFieldChanged(this.field.getValue());
	}

	/** Tag, item and mod names for autocomplete and validation; read once (tags come from the server's data). */
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
		tagSet = Set.copyOf(tagIds);
		itemSet = Set.copyOf(itemIds);
		modSet = Set.copyOf(modIds);
	}

	/** Tags change on datapack reload; forget the cached names when the player leaves a world. */
	public static void clearRegistryNames() {
		tagIds = null;
	}

	// ---------------------------------------------------------------- rules

	/** The rule the field currently describes, if its name exists in the game. */
	private Optional<Rule> currentRule(boolean exclude) {
		return Rule.of(this.kind, this.field.getValue(), exclude).filter(CardEditScreen::exists);
	}

	private static boolean exists(Rule rule) {
		return switch (rule.kind()) {
			case ITEM -> itemSet.contains(rule.value());
			case TAG -> tagSet.contains(rule.value());
			case MOD -> modSet.contains(rule.value());
		};
	}

	private void addRule(boolean exclude) {
		Optional<Rule> rule = currentRule(exclude);
		if (rule.isEmpty() || this.rules.size() >= SortingCard.MAX_RULES) {
			return;
		}
		String stored = rule.get().storage();
		// Including and excluding the same thing makes no sense: the newer choice replaces the older one.
		this.rules.remove(exclude ? stored.substring(1) : "!" + stored);
		if (!this.rules.contains(stored)) {
			this.rules.add(stored);
		}
		this.field.setValue("");
		this.scroll = Math.max(0, this.rules.size() - VISIBLE_ROWS);
	}

	private void onFieldChanged(String text) {
		if (this.updatingField) {
			return;
		}
		// Typing the old prefixes still works: they just pick the kind.
		String trimmed = text.stripLeading();
		Kind typed = trimmed.startsWith("#") ? Kind.TAG : trimmed.startsWith("@") ? Kind.MOD : null;
		if (typed != null) {
			this.updatingField = true;
			this.field.setValue(trimmed.substring(1));
			this.updatingField = false;
			this.kind = typed;
			this.kindButton.setMessage(kindLabel());
		}

		boolean valid = currentRule(false).isPresent();
		boolean empty = this.field.getValue().isBlank();
		this.field.setTextColor(valid || empty ? 0xFFFFFFFF : 0xFFFF6060);
		this.addButton.active = valid;
		this.excludeButton.active = valid;
		updateSuggestions();
	}

	private void updateSuggestions() {
		String text = this.field.getValue().trim().toLowerCase();
		List<Suggestion> result = new ArrayList<>();
		if (text.isEmpty()) {
			ItemStack ghost = this.menu.ghostItem();
			if (!ghost.isEmpty()) {
				Identifier id = BuiltInRegistries.ITEM.getKey(ghost.getItem());
				result.add(new Suggestion(Kind.ITEM, id.toString()));
				result.add(new Suggestion(Kind.MOD, id.getNamespace()));
				ghost.typeHolder().tags().map(tag -> tag.location().toString()).sorted()
						.forEach(tag -> result.add(new Suggestion(Kind.TAG, tag)));
			}
		} else {
			List<String> names = switch (this.kind) {
				case ITEM -> itemIds;
				case TAG -> tagIds;
				case MOD -> modIds;
			};
			names.stream()
					.filter(name -> name.contains(text) && !name.equals(text) && !name.equals("minecraft:" + text))
					.sorted(Comparator.comparing((String name) -> !(name.startsWith(text) || name.contains(":" + text))).thenComparing(String::length))
					.limit(MAX_SUGGESTIONS)
					.forEach(name -> result.add(new Suggestion(this.kind, name)));
		}
		this.suggestions = result;
		this.selectedSuggestion = 0;
	}

	private void acceptSuggestion(int index) {
		if (index < 0 || index >= this.suggestions.size()) {
			return;
		}
		Suggestion suggestion = this.suggestions.get(index);
		this.kind = suggestion.kind();
		this.kindButton.setMessage(kindLabel());
		this.field.setValue(suggestion.id());
		this.field.moveCursorToEnd(false);
	}

	private boolean suggestionsVisible() {
		return this.field.isFocused() && !this.suggestions.isEmpty();
	}

	/** First suggestion shown; the ghost item can have many tags, so the list scrolls with the selection. */
	private int suggestionOffset() {
		return Math.max(0, Math.min(this.selectedSuggestion - MAX_SUGGESTIONS + 1, this.suggestions.size() - MAX_SUGGESTIONS));
	}

	private int visibleSuggestions() {
		return Math.min(MAX_SUGGESTIONS, this.suggestions.size());
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
				if (currentRule(false).isPresent()) {
					addRule(false);
				} else {
					acceptSuggestion(this.selectedSuggestion);
				}
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
				acceptSuggestion(suggestionOffset() + (my - top) / SUGGESTION_HEIGHT);
				return true;
			}
		}

		for (int row = 0; row < VISIBLE_ROWS; row++) {
			int index = this.scroll + row;
			if (index < this.rules.size() && overRemove(mx, my, row)) {
				this.rules.remove(index);
				this.scroll = Math.max(0, Math.min(this.scroll, this.rules.size() - VISIBLE_ROWS));
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private static boolean overRemove(int mx, int my, int row) {
		int rowY = LIST_Y + row * ROW_HEIGHT;
		return mx >= WIDTH - 20 && mx < WIDTH - 9 && my >= rowY && my < rowY + ROW_HEIGHT;
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
		inset(graphics, x + LIST_X - 1, y + LIST_Y - 1, WIDTH - 14, VISIBLE_ROWS * ROW_HEIGHT + 2, 0xFFA8A8A8);
		inset(graphics, x + 61, y + FIELD_Y - 1, 108, 14, 0xFF000000);
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
			extractRule(graphics, this.rules.get(this.scroll + row), LIST_Y + row * ROW_HEIGHT, overRemove(mx, my, row));
		}
		if (this.rules.size() > VISIBLE_ROWS) {
			String range = (this.scroll + 1) + "-" + Math.min(this.rules.size(), this.scroll + VISIBLE_ROWS) + "/" + this.rules.size();
			graphics.text(this.font, range, WIDTH - 8 - this.font.width(range), 6, 0xFF606060, false);
		}

		ItemStack ghost = this.menu.ghostItem();
		String ghostText = ghost.isEmpty() ? Component.translatable("gui.copperpipes.card.ghost_hint").getString() : ghost.getHoverName().getString();
		graphics.text(this.font, this.font.plainSubstrByWidth(ghostText, WIDTH - 36), 30, CardEditMenu.GHOST_Y + 5, TEXT, false);
		graphics.text(this.font, Component.translatable("gui.copperpipes.card.priority", this.priority), 25, PRIORITY_Y + 3, TEXT, false);
	}

	/** One list row: "Tag  minecraft:logs"; excluded rules get a red "NOT" in front, missing names are red. */
	private void extractRule(GuiGraphicsExtractor graphics, String stored, int rowY, boolean removeHovered) {
		int x = LIST_X + 3;
		Optional<Rule> parsed = Rule.parse(stored);
		if (parsed.isEmpty()) {
			graphics.text(this.font, this.font.plainSubstrByWidth("? " + stored, WIDTH - 36), x, rowY + 2, TEXT_MISSING, false);
		} else {
			Rule rule = parsed.get();
			if (rule.negated()) {
				Component not = Component.translatable("gui.copperpipes.card.not");
				graphics.text(this.font, not, x, rowY + 2, TEXT_EXCLUDED, false);
				x += this.font.width(not) + 3;
			}
			Component kindName = Component.translatable(rule.kind().translationKey());
			graphics.text(this.font, kindName, x, rowY + 2, kindColor(rule.kind()), false);
			x += this.font.width(kindName) + 4;
			int color = exists(rule) ? (rule.negated() ? TEXT_EXCLUDED : TEXT_ID) : TEXT_MISSING;
			graphics.text(this.font, this.font.plainSubstrByWidth(rule.value(), WIDTH - 22 - x), x, rowY + 2, color, false);
		}
		graphics.text(this.font, "x", WIDTH - 17, rowY + 2, removeHovered ? 0xFFFF4040 : 0xFF707070, false);
	}

	private static int kindColor(Kind kind) {
		return switch (kind) {
			case ITEM -> 0xFF2050A0;
			case TAG -> 0xFF1F7A2A;
			case MOD -> 0xFF7A2A9A;
		};
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		if (!suggestionsVisible()) {
			return;
		}
		graphics.nextStratum();
		int x = this.leftPos;
		int top = this.topPos + suggestionsTop();
		graphics.fill(x + LIST_X - 1, top - 1, x + WIDTH - 7, this.topPos + FIELD_Y - 1, 0xF0101010);
		int offset = suggestionOffset();
		for (int i = 0; i < visibleSuggestions(); i++) {
			Suggestion suggestion = this.suggestions.get(offset + i);
			boolean selected = offset + i == this.selectedSuggestion;
			int lineY = top + i * SUGGESTION_HEIGHT;
			if (selected) {
				graphics.fill(x + LIST_X, lineY, x + WIDTH - 8, lineY + SUGGESTION_HEIGHT, 0xFF3A3A6A);
			}
			Component kindName = Component.translatable(suggestion.kind().translationKey());
			int kindWidth = this.font.width(kindName);
			graphics.text(this.font, this.font.plainSubstrByWidth(suggestion.id(), WIDTH - 24 - kindWidth), x + LIST_X + 2, lineY + 2,
					selected ? 0xFFFFFF80 : 0xFFE0E0E0, false);
			graphics.text(this.font, kindName, x + WIDTH - 10 - kindWidth, lineY + 2, 0xFF909090, false);
		}
		if (this.suggestions.size() > MAX_SUGGESTIONS) {
			String count = (this.selectedSuggestion + 1) + "/" + this.suggestions.size();
			graphics.text(this.font, count, x + WIDTH - 8 - this.font.width(count), top - 10, 0xFFFFFFFF, true);
		}
	}

	private int suggestionsTop() {
		return FIELD_Y - 1 - visibleSuggestions() * SUGGESTION_HEIGHT;
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
