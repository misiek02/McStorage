package pl.misiek.copperpipes.network;

/**
 * A cached path from an outlet to an inlet.
 *
 * @param inlet       inlet connector position (BlockPos as long)
 * @param path        positions from the first block after the outlet up to and including the inlet
 * @param times       ticks after leaving the outlet at which an item reaches the centre of each {@code path} block
 * @param travelTicks how long an item takes to cover the whole path
 */
public record Route(long inlet, long[] path, float[] times, int travelTicks) {
}
