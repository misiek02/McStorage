import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Generates PLACEHOLDER 16x16 textures (to be replaced with real art later).
 * Run from the repo root: java tools/GenTextures.java
 */
public class GenTextures {
	static final String DIR = "src/main/resources/assets/copperpipes/textures/block/";

	public static void main(String[] args) throws Exception {
		new File(DIR).mkdirs();
		pipe("copper_pipe", 0xC86F4A, 0xE0956E, 0x8F4A30);
	}

	/** Copper frame (2px border with a light/dark bevel) around a pale glass-blue centre. */
	static void pipe(String name, int frame, int light, int dark) throws Exception {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				boolean border = x < 2 || y < 2 || x > 13 || y > 13;
				int rgb;
				if (border) {
					rgb = (x == 0 || y == 0) ? light : (x == 15 || y == 15) ? dark : frame;
				} else {
					// Glass: pale blue with a diagonal highlight streak.
					rgb = (x - y == 3 || x - y == 4) ? 0xEAF6FA : 0xB8DCE6;
				}
				img.setRGB(x, y, 0xFF000000 | rgb);
			}
		}
		ImageIO.write(img, "png", new File(DIR + name + ".png"));
		System.out.println("wrote " + name + ".png");
	}
}
