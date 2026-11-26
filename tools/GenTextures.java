import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Generates PLACEHOLDER 16x16 textures (to be replaced with real art later).
 * Run from the repo root: java tools/GenTextures.java
 *
 * Pipe models sample the 8x8 centre of the frame texture (pixels 4..11), so the frame lines sit on
 * rows/columns 4 and 11 and run across the whole texture (arm and item faces sample longer strips).
 */
public class GenTextures {
	static final String DIR = "src/main/resources/assets/copperpipes/textures/block/";
	static final int COPPER_LIGHT = 0xE0805A;
	static final int COPPER_DARK = 0xB4532F;
	static final int ARROW = 0xF6B48C;

	public static void main(String[] args) throws Exception {
		new File(DIR).mkdirs();
		write(frame(COPPER_LIGHT, COPPER_DARK), "copper_pipe_frame");
		write(frame(0xBE9B83, 0x8E6E5A), "exposed_copper_pipe_frame");
		write(frame(0x86B98A, 0x56875A), "weathered_copper_pipe_frame");
		write(frame(0x6FC3A4, 0x3F8A6E), "oxidized_copper_pipe_frame");
		glass("copper_pipe_glass");

		// Connector body sides: frame + arrow. Models map "left" in the _h texture and "up" in the _v texture
		// towards the attached container, so the inlet arrow points there and the outlet arrow points away.
		BufferedImage towardContainer = arrowLeft();
		BufferedImage awayFromContainer = mirror(towardContainer);
		write(towardContainer, "inlet_connector_arrow_h");
		write(transpose(towardContainer), "inlet_connector_arrow_v");
		write(awayFromContainer, "outlet_connector_arrow_h");
		write(transpose(awayFromContainer), "outlet_connector_arrow_v");
	}

	/** Copper edge lines: light on rows/cols 4, dark on rows/cols 11, transparent elsewhere (cutout). */
	static BufferedImage frame(int light, int dark) {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int argb = 0;
				if (x == 11 || y == 11) argb = 0xFF000000 | dark;
				if (x == 4 || y == 4) argb = 0xFF000000 | light;
				img.setRGB(x, y, argb);
			}
		}
		return img;
	}

	/** Horizontal frame lines (rows 4 and 11) with an arrow pointing left in between. */
	static BufferedImage arrowLeft() {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int x = 0; x < 16; x++) {
			img.setRGB(x, 4, 0xFF000000 | COPPER_LIGHT);
			img.setRGB(x, 11, 0xFF000000 | COPPER_DARK);
		}
		int arrow = 0xFF000000 | ARROW;
		img.setRGB(4, 7, arrow);
		img.setRGB(4, 8, arrow);
		for (int y = 6; y <= 9; y++) img.setRGB(5, y, arrow);
		for (int y = 5; y <= 10; y++) img.setRGB(6, y, arrow);
		for (int x = 7; x <= 11; x++) {
			img.setRGB(x, 7, arrow);
			img.setRGB(x, 8, arrow);
		}
		return img;
	}

	static BufferedImage mirror(BufferedImage src) {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				img.setRGB(15 - x, y, src.getRGB(x, y));
			}
		}
		return img;
	}

	static BufferedImage transpose(BufferedImage src) {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				img.setRGB(y, x, src.getRGB(x, y));
			}
		}
		return img;
	}

	/** Faint blue-white glass with two diagonal highlight streaks; every pixel is partly transparent (translucent). */
	static void glass(String name) throws Exception {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int d = (x + y) % 16;
				int argb = 0x1ED8EEF4;
				if (d == 9 || d == 10) argb = 0x58F4FCFF;
				if (d == 12) argb = 0x3CF4FCFF;
				img.setRGB(x, y, argb);
			}
		}
		write(img, name);
	}

	static void write(BufferedImage img, String name) throws Exception {
		ImageIO.write(img, "png", new File(DIR + name + ".png"));
		System.out.println("wrote " + name + ".png");
	}
}
