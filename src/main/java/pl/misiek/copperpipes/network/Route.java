package pl.misiek.copperpipes.network;

/**
 * A cached path from an outlet to an inlet.
 *
 * @param inlet       inlet connector position (BlockPos as long)
 * @param path        positions from the first block after the outlet up to and including the inlet
 * @param travelTicks how long an item takes to cover the path
 */
public record Route(long inlet, long[] path, int travelTicks) {
}
