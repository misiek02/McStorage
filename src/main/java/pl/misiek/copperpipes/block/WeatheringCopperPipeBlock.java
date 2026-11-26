package pl.misiek.copperpipes.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;

/** Unwaxed pipe: oxidizes over time through vanilla random ticks, exactly like copper blocks. */
public class WeatheringCopperPipeBlock extends CopperPipeBlock implements WeatheringCopper {
	public WeatheringCopperPipeBlock(WeatheringCopper.WeatherState weatherState, Properties properties) {
		super(weatherState, properties);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		this.changeOverTime(state, level, pos, random);
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return WeatheringCopper.getNext(state.getBlock()).isPresent();
	}

	@Override
	public WeatheringCopper.WeatherState getAge() {
		return super.getAge();
	}
}
