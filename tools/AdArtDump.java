import com.hopeless.infinity.planet.*;
/** AdArtDump out.png [billboard]: writes the Cobble & Sons advertisement picture (planet/AdArt: the slides stacked) exactly as the client
 *  uploads it; with "billboard", the billboards' messages (AdArt.billboardAtlas, 0.28.4). Saved tool. */
public class AdArtDump { public static void main(String[] a) throws Exception { boolean bb = a.length > 1 && a[1].equals("billboard");
  javax.imageio.ImageIO.write(bb ? AdArt.billboardAtlas() : AdArt.atlas(), "png", new java.io.File(a[0])); System.out.println("wrote " + a[0] + (bb ? " " + AdArt.BW + " x " + AdArt.BH * AdArt.BSLIDES : " " + AdArt.W + " x " + AdArt.H * AdArt.SLIDES)); } }
