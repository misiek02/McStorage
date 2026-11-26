package pl.misiek.copperpipes.card;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Data component stored on a sorting card item.
 *
 * @param rules     filter rules: {@code #namespace:tag}, {@code namespace:item}, {@code @modid}, each optionally
 *                  prefixed with {@code !} to exclude
 * @param priority  0-9; inlets with a higher priority are filled first
 * @param blacklist when true the card accepts everything its rules do NOT match
 */
public record SortingCard(List<String> rules, int priority, boolean blacklist) {
	public static final int MAX_PRIORITY = 9;
	public static final int MAX_RULES = 32;
	public static final SortingCard EMPTY = new SortingCard(List.of(), 0, false);

	public static final Codec<SortingCard> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.listOf(0, MAX_RULES).optionalFieldOf("rules", List.of()).forGetter(SortingCard::rules),
			Codec.intRange(0, MAX_PRIORITY).optionalFieldOf("priority", 0).forGetter(SortingCard::priority),
			Codec.BOOL.optionalFieldOf("blacklist", false).forGetter(SortingCard::blacklist)
	).apply(instance, SortingCard::new));

	public static final StreamCodec<ByteBuf, SortingCard> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_RULES)), SortingCard::rules,
			ByteBufCodecs.VAR_INT, SortingCard::priority,
			ByteBufCodecs.BOOL, SortingCard::blacklist,
			SortingCard::new);

	public SortingCard {
		rules = List.copyOf(rules);
		priority = Math.clamp(priority, 0, MAX_PRIORITY);
	}
}
