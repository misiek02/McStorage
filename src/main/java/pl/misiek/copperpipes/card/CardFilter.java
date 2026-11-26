package pl.misiek.copperpipes.card;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
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
		public enum Kind { TAG, ITEM, MOD }

		/** Parses {@code [!](#ns:tag | ns:item | @modid)}; empty if the syntax is invalid. */
		public static Optional<Rule> parse(String raw) {
			String text = raw.trim();
			boolean negated = text.startsWith("!");
			if (negated) {
				text = text.substring(1).trim();
			}
			if (text.startsWith("#")) {
				Identifier id = Identifier.tryParse(text.substring(1));
				return id == null ? Optional.empty() : Optional.of(new Rule(Kind.TAG, id.toString(), negated));
			}
			if (text.startsWith("@")) {
				String modId = text.substring(1);
				return Identifier.isValidNamespace(modId) && !modId.isEmpty()
						? Optional.of(new Rule(Kind.MOD, modId, negated)) : Optional.empty();
			}
			Identifier id = text.isEmpty() ? null : Identifier.tryParse(text);
			return id == null ? Optional.empty() : Optional.of(new Rule(Kind.ITEM, id.toString(), negated));
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
