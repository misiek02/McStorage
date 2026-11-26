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

	public static void main(String[] args) throws Exception {
		new File(DIR).mkdirs();
		frame("copper_pipe_frame", 0xE0805A, 0xB4532F);
		frame("exposed_copper_pipe_frame", 0xBE9B83, 0x8E6E5A);
		frame("weathered_copper_pipe_frame", 0x86B98A, 0x56875A);
		frame("oxidized_copper_pipe_frame", 0x6FC3A4, 0x3F8A6E);
		glass("copper_pipe_glass");
		connectorFace("inlet_connector_face", 0x4FC46A, 0x2E7D40);
		connectorFace("outlet_connector_face", 0x4F8FE0, 0x2D5A9A);
		new File(DIR + "copper_pipe.png").delete(); // old milestone 2 texture
	}

	/** Copper edge lines: light on rows/cols 4, dark on rows/cols 11, transparent elsewhere (cutout). */
	static void frame(String name, int light, int dark) throws Exception {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int argb = 0;
				if (x == 11 || y == 11) argb = 0xFF000000 | dark;
				if (x == 4 || y == 4) argb = 0xFF000000 | light;
				img.setRGB(x, y, argb);
			}
		}
		write(img, name);
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

	/** Copper plate with a coloured 6x6 marker in the middle (green = inlet, blue = outlet). */
	static void connectorFace(String name, int marker, int markerDark) throws Exception {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int rgb = ((x + 2 * y) % 5 == 0) ? 0xD07650 : 0xC0643F;
				if (x == 2 || y == 2 || x == 13 || y == 13) rgb = 0x8F4A30;
				if (x >= 5 && x <= 10 && y >= 5 && y <= 10) {
					rgb = (x == 5 || y == 5 || x == 10 || y == 10) ? markerDark : marker;
				}
				img.setRGB(x, y, 0xFF000000 | rgb);
			}
		}
		write(img, name);
	}

	static void write(BufferedImage img, String name) throws Exception {
		ImageIO.write(img, "png", new File(DIR + name + ".png"));
		System.out.println("wrote " + name + ".png");
	}
}
