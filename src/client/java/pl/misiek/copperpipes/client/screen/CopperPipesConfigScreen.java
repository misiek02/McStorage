package pl.misiek.copperpipes.client.screen;

import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import pl.misiek.copperpipes.client.ClientConfig;

/** Settings screen for pipe item rendering, opened from the Options screen or with /cpconfig. */
public class CopperPipesConfigScreen extends Screen {
	/** The slider offers 0..this; the config itself (and /cpconfig) allows more. */
	private static final int SLIDER_MAX_ITEMS = 5000;
	private static final int ITEM_STEP = 50;

	private final Screen parent;

	public CopperPipesConfigScreen(Screen parent) {
		super(Component.translatable("gui.copperpipes.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		ClientConfig config = ClientConfig.get();
		int x = this.width / 2 - 100;
		int y = this.height / 4 + 8;

		this.addRenderableWidget(CycleButton.onOffBuilder(config.renderItems)
				.create(x, y, 200, 20, Component.translatable("gui.copperpipes.config.render"), (button, value) -> config.renderItems = value));

		this.addRenderableWidget(new IntSlider(x, y + 26, 200, "gui.copperpipes.config.distance", 1, ClientConfig.MAX_RENDER_DISTANCE, 1,
				config.renderDistance, value -> config.renderDistance = value));

		this.addRenderableWidget(new IntSlider(x, y + 52, 200, "gui.copperpipes.config.max_items", 0, SLIDER_MAX_ITEMS, ITEM_STEP,
				Math.min(config.maxVisibleItems, SLIDER_MAX_ITEMS), value -> config.maxVisibleItems = value));

		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
				.bounds(x, y + 100, 200, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, this.height / 4 - 16, 0xFFFFFFFF);
		graphics.centeredText(this.font, Component.translatable("gui.copperpipes.config.hint"), this.width / 2, this.height / 4 + 82, 0xFFA0A0A0);
	}

	@Override
	public void onClose() {
		ClientConfig.save();
		this.minecraft.gui.setScreen(this.parent);
	}

	/** A slider over whole numbers from min to max, snapped to step. */
	private static final class IntSlider extends AbstractSliderButton {
		private final String key;
		private final int min;
		private final int max;
		private final int step;
		private final IntConsumer onChange;

		IntSlider(int x, int y, int width, String key, int min, int max, int step, int initial, IntConsumer onChange) {
			super(x, y, width, 20, Component.empty(), (double) (initial - min) / (max - min));
			this.key = key;
			this.min = min;
			this.max = max;
			this.step = step;
			this.onChange = onChange;
			this.updateMessage();
		}

		private int intValue() {
			int raw = this.min + (int) Math.round(this.value * (this.max - this.min));
			return Mth.clamp(Math.round((float) raw / this.step) * this.step, this.min, this.max);
		}

		@Override
		protected void updateMessage() {
			this.setMessage(Component.translatable(this.key, this.intValue()));
		}

		@Override
		protected void applyValue() {
			this.onChange.accept(this.intValue());
		}
	}
}
