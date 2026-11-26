package pl.misiek.copperpipes.card;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * A sorting card compiled for fast matching: tags and mod ids are expanded into item sets once, so matching an
 * item is two set lookups.
 *
 * <p>Semantics: an item matches when it hits any positive rule (or there are no positive rules but at least one
 * exclusion) and hits no exclusion. Blacklist mode inverts the result. An empty whitelist card matches nothing.
 */
public final class CardFilter {
	private final Set<Item> included = new HashSet<>();
	private final Set<Item> excluded = new HashSet<>();
	private boolean hasPositiveRule;
	private boolean hasExclusion;
	private final boolean blacklist;
	private final int priority;

	private CardFilter(boolean blacklist, int priority) {
		this.blacklist = blacklist;
		this.priority = priority;
	}

	public static CardFilter compile(SortingCard card) {
		CardFilter filter = new CardFilter(card.blacklist(), card.priority());
		for (String raw : card.rules()) {
			Optional<Rule> rule = Rule.parse(raw);
			if (rule.isEmpty()) {
				continue; // invalid rules are ignored; the card GUI points them out
			}
			Rule r = rule.get();
			Set<Item> target = r.negated() ? filter.excluded : filter.included;
			if (r.negated()) {
				filter.hasExclusion = true;
			} else {
				filter.hasPositiveRule = true;
			}
			r.expandInto(target);
		}
		return filter;
	}

	public boolean matches(Item item) {
		boolean base = this.hasPositiveRule ? this.included.contains(item) : this.hasExclusion;
		boolean result = base && !this.excluded.contains(item);
		return this.blacklist != result;
	}

	public int priority() {
		return this.priority;
	}

	/** One parsed rule. */
	public record Rule(Kind kind, String value, boolean negated) {
		public enum Kind {
			ITEM("item"), TAG("tag"), MOD("mod");

			private final String key;

			Kind(String key) {
				this.key = key;
			}

			/** Translation key of the kind's name, e.g. {@code gui.copperpipes.rule.kind.tag}. */
			public String translationKey() {
				return "gui.copperpipes.rule.kind." + this.key;
			}

			public Kind next() {
				return values()[(this.ordinal() + 1) % values().length];
			}
		}

		/** Parses the stored form {@code [!](#ns:tag | ns:item | @modid)}; empty if the syntax is invalid. */
		public static Optional<Rule> parse(String raw) {
			String text = raw.trim();
			boolean negated = text.startsWith("!");
			if (negated) {
				text = text.substring(1).trim();
			}
			if (text.startsWith("#")) {
				return of(Kind.TAG, text.substring(1), negated);
			}
			if (text.startsWith("@")) {
				return of(Kind.MOD, text.substring(1), negated);
			}
			return of(Kind.ITEM, text, negated);
		}

		/** Builds a rule from a kind and a plain id (no prefix characters); empty if the id is malformed. */
		public static Optional<Rule> of(Kind kind, String id, boolean negated) {
			String text = id.trim();
			if (text.isEmpty()) {
				return Optional.empty();
			}
			if (kind == Kind.MOD) {
				return Identifier.isValidNamespace(text) ? Optional.of(new Rule(kind, text, negated)) : Optional.empty();
			}
			Identifier parsed = Identifier.tryParse(text);
			return parsed == null ? Optional.empty() : Optional.of(new Rule(kind, parsed.toString(), negated));
		}

		/** The form stored on the card. */
		public String storage() {
			String prefix = switch (this.kind) {
				case ITEM -> "";
				case TAG -> "#";
				case MOD -> "@";
			};
			return (this.negated ? "!" : "") + prefix + this.value;
		}

		/** Whether the item, tag or mod this rule names is actually present in the game. */
		public boolean exists() {
			return switch (this.kind) {
				case ITEM -> BuiltInRegistries.ITEM.containsKey(Identifier.parse(this.value));
				case TAG -> BuiltInRegistries.ITEM.get(TagKey.create(Registries.ITEM, Identifier.parse(this.value))).isPresent();
				case MOD -> BuiltInRegistries.ITEM.keySet().stream().anyMatch(id -> id.getNamespace().equals(this.value));
			};
		}

		/** Readable description, e.g. "Tag: minecraft:logs" or "Exclude item: minecraft:dirt". */
		public Component describe() {
			Component kindName = Component.translatable(this.kind.translationKey());
			return Component.translatable(this.negated ? "gui.copperpipes.rule.exclude" : "gui.copperpipes.rule.include", kindName, this.value);
		}

		void expandInto(Set<Item> items) {
			switch (this.kind) {
				case TAG -> {
					TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(this.value));
					for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
						items.add(holder.value());
					}
				}
				case ITEM -> BuiltInRegistries.ITEM.getOptional(Identifier.parse(this.value)).ifPresent(items::add);
				case MOD -> {
					for (Item item : BuiltInRegistries.ITEM) {
						if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(this.value)) {
							items.add(item);
						}
					}
				}
			}
		}
	}
}
