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
		sortingCard();

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
		// Small 4x4 arrow centred on the face.
		img.setRGB(6, 7, arrow);
		img.setRGB(6, 8, arrow);
		for (int y = 6; y <= 9; y++) img.setRGB(7, y, arrow);
		for (int x = 8; x <= 9; x++) {
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

	/** Paper card with a copper stripe and a few "rule" lines. */
	static void sortingCard() throws Exception {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 3; y <= 12; y++) {
			for (int x = 2; x <= 13; x++) {
				int rgb = 0xEDE6D6;
				if (x == 2 || y == 3 || x == 13 || y == 12) rgb = 0xA89F8A;
				else if (y == 4 || y == 5) rgb = COPPER_LIGHT;
				else if ((y == 7 || y == 9 || y == 11) && x >= 4 && x <= (y == 9 ? 9 : 11)) rgb = 0x6B6457;
				img.setRGB(x, y, 0xFF000000 | rgb);
			}
		}
		ImageIO.write(img, "png", new File(DIR.replace("block/", "item/") + "sorting_card.png"));
		System.out.println("wrote sorting_card.png");
	}

	static void write(BufferedImage img, String name) throws Exception {
		ImageIO.write(img, "png", new File(DIR + name + ".png"));
		System.out.println("wrote " + name + ".png");
	}
}
