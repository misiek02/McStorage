package pl.misiek.copperpipes.network;

import net.minecraft.world.item.ItemStack;

/** A stack travelling through pipes; delivered to its inlet at {@code arrivalTick}. */
record Transit(ItemStack stack, long inlet, long[] path, long arrivalTick) {
	boolean passesThrough(long pos) {
		for (long p : this.path) {
			if (p == pos) {
				return true;
			}
		}
		return false;
	}

	Transit delayedTo(long tick) {
		return new Transit(this.stack, this.inlet, this.path, tick);
	}
}
