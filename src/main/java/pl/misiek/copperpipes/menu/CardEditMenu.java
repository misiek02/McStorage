package pl.misiek.copperpipes.menu;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import pl.misiek.copperpipes.registry.ModItems;
import pl.misiek.copperpipes.registry.ModMenus;

/**
 * Menu behind the card editor. It only exists to show the player inventory and a ghost slot: clicking the ghost
 * slot with an item shows a copy of it (nothing is moved) so the screen can suggest that item's rules.
 * The card itself is edited client-side and saved with {@link pl.misiek.copperpipes.packet.UpdateCardPayload}.
 */
public class CardEditMenu extends AbstractContainerMenu {
	public static final int GHOST_SLOT = 0;
	public static final int GHOST_X = 8;
	public static final int GHOST_Y = 130;
	public static final int INVENTORY_TOP = 176;
	private static final int FIRST_HOTBAR_SLOT = 1 + 27;

	private final SimpleContainer ghost = new SimpleContainer(1);
	/** Menu slot holding the card being edited (main hand only), locked so the card cannot be moved away. */
	private final int lockedSlot;
	private final int lockedHotbarIndex;

	public CardEditMenu(int containerId, Inventory inventory) {
		super(ModMenus.CARD_EDIT, containerId);
		this.addSlot(new Slot(this.ghost, 0, GHOST_X, GHOST_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}

			@Override
			public boolean mayPickup(Player player) {
				return false;
			}
		});
		this.addStandardInventorySlots(inventory, 8, INVENTORY_TOP);

		boolean mainHand = cardHand(inventory.player) == InteractionHand.MAIN_HAND;
		this.lockedHotbarIndex = mainHand ? inventory.getSelectedSlot() : -1;
		this.lockedSlot = mainHand ? FIRST_HOTBAR_SLOT + this.lockedHotbarIndex : -1;
	}

	/** The hand holding the card being edited: the main hand wins, as it does for item use. Null if none. */
	public static InteractionHand cardHand(Player player) {
		if (player.getMainHandItem().is(ModItems.SORTING_CARD)) {
			return InteractionHand.MAIN_HAND;
		}
		return player.getOffhandItem().is(ModItems.SORTING_CARD) ? InteractionHand.OFF_HAND : null;
	}

	public ItemStack ghostItem() {
		return this.ghost.getItem(0);
	}

	@Override
	public void clicked(int slotIndex, int buttonNum, ContainerInput input, Player player) {
		if (slotIndex == GHOST_SLOT) {
			ItemStack carried = this.getCarried();
			this.ghost.setItem(0, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
			return;
		}
		if (slotIndex == this.lockedSlot || (input == ContainerInput.SWAP && buttonNum == this.lockedHotbarIndex)) {
			return;
		}
		super.clicked(slotIndex, buttonNum, input, player);
	}

	/** Shift-click on an inventory item shows it in the ghost slot instead of moving it. */
	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		Slot slot = this.slots.get(slotIndex);
		if (slotIndex != GHOST_SLOT && slot.hasItem()) {
			this.ghost.setItem(0, slot.getItem().copyWithCount(1));
		}
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return cardHand(player) != null;
	}
}
