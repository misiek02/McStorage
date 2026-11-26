package pl.misiek.copperpipes.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import pl.misiek.copperpipes.card.CardFilter;
import pl.misiek.copperpipes.item.SortingCardItem;
import pl.misiek.copperpipes.network.PipeNetworks;
import pl.misiek.copperpipes.registry.ModBlockEntities;

/** Inlet state: one sorting card slot (separate from the container's own slots). No card = overflow inlet. */
public class InletBlockEntity extends BlockEntity {
	private ItemStack card = ItemStack.EMPTY;

	public InletBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.INLET, pos, state);
	}

	public ItemStack getCard() {
		return this.card;
	}

	public void setCard(ItemStack card) {
		this.card = card.isEmpty() ? ItemStack.EMPTY : card.copyWithCount(1);
		this.setChanged();
		if (this.level instanceof ServerLevel serverLevel) {
			serverLevel.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3); // sync card to clients
			PipeNetworks.get(serverLevel).onNodeChanged(this.worldPosition); // filters are compiled per network
		}
	}

	/** Compiled filter, or null for an overflow inlet. */
	public CardFilter compileFilter() {
		return this.card.isEmpty() ? null : CardFilter.compile(SortingCardItem.cardOf(this.card));
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.card = input.read("Card", ItemStack.CODEC).orElse(ItemStack.EMPTY);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!this.card.isEmpty()) {
			output.store("Card", ItemStack.CODEC, this.card);
		}
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return this.saveCustomOnly(registries);
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (this.level != null && !this.card.isEmpty()) {
			Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, this.card);
		}
	}
}
