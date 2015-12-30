package hageldave.imagingkit.core;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.image.DirectColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.concurrent.CountedCompleter;
import java.util.function.Consumer;

public class Img implements Iterable<Pixel> {

	public static final int boundary_mode_zero = 0;
	public static final int boundary_mode_repeat_edge = 1;
	public static final int boundary_mode_repeat_image = 2;
	public static final int boundary_mode_mirror = 3;
	
	private final int[] data;
	private final Dimension dimension;
	
	/**
	 * Creates a new Img of given dimensions.
	 * Values are initialized to 0.
	 * @param width
	 * @param height
	 */
	public Img(int width, int height){
		this(new Dimension(width, height));
	}
	
	/**
	 * Creates a new Img of given Dimension.
	 * Values are initilaized to 0.
	 * @param dimension
	 */
	public Img(Dimension dimension){
		this.data = new int[dimension.width*dimension.height];
		this.dimension = new Dimension(dimension);
	}
	
	/**
	 * Creates a new Img of same dimensions as provided BufferedImage.
	 * Values are copied from argument Image
	 * @param bimg
	 * @see #createRemoteImg(BufferedImage)
	 */
	public Img(BufferedImage bimg){
		this(bimg.getWidth(), bimg.getHeight());
		bimg.getRGB(0, 0, this.getWidth(), this.getHeight(), this.getData(), 0, this.getWidth());
	}
	
	/**
	 * Creates a new Img of privided dimensions.
	 * Provided data array will be used as this images data.
	 * @param width
	 * @param height
	 * @param data
	 */
	public Img(int width, int height, int[] data){
		this(new Dimension(width, height), data);
	}
	
	/**
	 * Creates a new Img of privided dimensions.
	 * Provided data array will be used as this images data.
	 * @param dim
	 * @param data
	 */
	public Img(Dimension dim, int[] data){
		if(dim.width*dim.height != data.length){
			throw new IllegalArgumentException(String.format("Provided Dimension %s does not match number of provided pixels %d", dim, data.length));
		}
		this.dimension = new Dimension(dim);
		this.data = data;
	}
	
	/**
	 * @return dimension of this Img
	 */
	public Dimension getDimension() {
		return new Dimension(dimension);
	}
	
	/**
	 * @return width of this Img
	 */
	public int getWidth(){
		return dimension.width;
	}
	
	/**
	 * @return height of this Img
	 */
	public int getHeight(){
		return dimension.height;
	}
	
	/**
	 * @return number of values (pixels) of this Img
	 */
	public int numValues(){
		return getWidth()*getHeight();
	}
	
	/**
	 * @return data array of this Img
	 */
	public int[] getData() {
		return data;
	}
	
	/**
	 * Returns the value of this Img at the specified position.
	 * No bounds checks will be performed, positions outside of this
	 * images dimension can either result in a value for a different position
	 * or an ArrayIndexOutOfBoundsException.
	 * @param x
	 * @param y
	 * @return value for specified position
	 * @throws ArrayIndexOutOfBoundsException if resulting index from x and y
	 * is not within the data arrays bounds.
	 * @see #getValue(int, int, int)
	 * @see #setValue(int, int, int)
	 */
	public int getValue(final int x, final int y){
		return this.data[y*dimension.width + x];
	}
	
	/**
	 * Returns the value of this Img at the specified position.
	 * Bounds checks will be performed and positions outside of this images
	 * dimensions will be handled according to the specified boundary mode.
	 * <p>
	 * <b><u>Boundary Modes</u></b><br>
	 * {@link #boundary_mode_zero} <br> 
	 * will return 0 for out of bounds positions. 
	 * <br>
	 * -{@link #boundary_mode_repeat_edge} <br>
	 * will return the same value as the nearest edge value.
	 * <br>
	 * -{@link #boundary_mode_repeat_image} <br>
	 * will return a value of the image as if the if the image was repeated on 
	 * all sides.
	 * <br>
	 * -{@link #boundary_mode_mirror} <br>
	 * will return a value of the image as if the image was mirrored on all 
	 * sides.
	 * <br>
	 * -<u>other values for boundary mode </u><br>
	 * will be used as default color for out of bounds positions. It is safe 
	 * to use opaque colors (0xff000000 - 0xffffffff) and transparent colors 
	 * above 0x0000000f which will not collide with one of the boundary modes 
	 * (number of boundary modes is limited to 16 for the future).
	 * @param x
	 * @param y
	 * @param boundaryMode
	 * @return value at specified position or a value depending on the
	 * boundary mode for out of bounds positions.
	 */
	public int getValue(int x, int y, final int boundaryMode){
		if(x < 0 || y < 0 || x >= dimension.width || y >= dimension.height){
			switch (boundaryMode) {
			case boundary_mode_zero:
				return 0;
			case boundary_mode_repeat_edge:
				x = (x < 0 ? 0: (x >= dimension.width ? dimension.width-1:x));
				y = (y < 0 ? 0: (y >= dimension.height ? dimension.height-1:y));
				return getValue(x, y);
			case boundary_mode_repeat_image:
				x = (dimension.width + (x % dimension.width)) % dimension.width;
				y = (dimension.height + (y % dimension.height)) % dimension.height;
				return getValue(x,y);
			case boundary_mode_mirror:
				if(x < 0){ // mirror x to right side of image
					x = -x - 1; 
				}
				if(y < 0 ){ // mirror y to bottom side of image
					y = -y - 1;
				}
				x = (x/dimension.width) % 2 == 0 ? (x%dimension.width) : (dimension.width-1)-(x%dimension.width);
				y = (y/dimension.height) % 2 == 0 ? (y%dimension.height) : (dimension.height-1)-(y%dimension.height);
				return getValue(x, y);
			default:
				return boundaryMode; // boundary mode can be default color
			}
		} else { 
			return getValue(x, y);
		}
	}
	
	/**
	 * Returns a bilinearly interpolated ARGB value of the image for the 
	 * specified normalized position (x and y within [0,1]). Position {0,0} 
	 * denotes the images origin (top left corner), Position {1,1} denotes the 
	 * opposite corner (pixel at {width-1, height-1}). 
	 * <p>
	 * An IndexOutOfBoundsException may be thrown for x and y greater than 1 
	 * or less than 0.
	 * @param xNormalized
	 * @param yNormalized
	 * @throws ArrayIndexOutOfBoundsException when a resulting index is out of 
	 * the data arrays bounds, which can only happen for x and y values less 
	 * than 0 or greater than 1.
	 * @return bilinearly interpolated ARGB value.
	 */
	public int interpolateARGB(final float xNormalized, final float yNormalized){
		float xF = xNormalized * (getWidth()-1);
		float yF = yNormalized * (getHeight()-1);
		int x = (int)xF;
		int y = (int)yF;
		int c00 = getValue(x, 							y);
		int c01 = getValue(x, 						   (y+1 < getHeight() ? y+1:y));
		int c10 = getValue((x+1 < getWidth() ? x+1:x), 	y);
		int c11 = getValue((x+1 < getWidth() ? x+1:x), (y+1 < getHeight() ? y+1:y));
		return interpolateColors(c00, c01, c10, c11, xF-x, yF-y);
	}
	
	private static int interpolateColors(final int c00, final int c01, final int c10, final int c11, final float mx, final float my){
		return argb_fast/*_bounded*/(
				blend( blend(a(c00), a(c01), mx), blend(a(c10), a(c11), mx), my),
				blend( blend(r(c00), r(c01), mx), blend(r(c10), r(c11), mx), my),
				blend( blend(g(c00), g(c01), mx), blend(g(c10), g(c11), mx), my),
				blend( blend(b(c00), b(c01), mx), blend(b(c10), b(c11), mx), my) );
	}
	
	private static int blend(final int channel1, final int channel2, final float m){
		return (int) ((channel2 * m) + (channel1 * (1f-m)));
	}
	
	/**
	 * Creates a new Pixel object for this Img with position {0,0}.
	 * @return a Pixel object for this Img.
	 */
	public Pixel getPixel(){
		return new Pixel(this, 0);
	}
	
	/**
	 * Creates a new Pixel object for this Img at specified position.
	 * @param x
	 * @param y
	 * @return a Pixel object for this Img at {x,y}.
	 * @see #getValue(int, int)
	 */
	public Pixel getPixel(int x, int y){
		return new Pixel(this, x,y);
	}
	
	/**
	 * Copies specified area of this Img to the specified destination Img
	 * at specified destination coordinates. If destination Img is null a new
	 * Img with the areas size will be created and the destination coordinates
	 * will be ignored so that the Img will contain all the values of the area.
	 * <p>
	 * The specified area has to be within the bounds of this image or
	 * otherwise an IllegalArgumentException will be thrown. Only the 
	 * intersecting part of the area and the destination image is copied which 
	 * allows for an out of bounds destination area origin.
	 * 
	 * @param x area origin in this image (x-coordinate)
	 * @param y area origin in this image (y-coordinate)
	 * @param w width of area
	 * @param h height of area
	 * @param dest destination Img
	 * @param destX area origin in destination Img (x-coordinate)
	 * @param destY area origin in destination Img (y-coordinate)
	 * @return the destination Img
	 * @throws IllegalArgumentException if the specified area is not within 
	 * the bounds of this Img.
	 */
	public Img copyArea(int x, int y, int w, int h, Img dest, int destX, int destY){
		if(x < 0 || y < 0 || x+w > getWidth() || y+h > getHeight()){
			throw new IllegalArgumentException("specified area is not within image bounds!");
		}
		if(dest == null){
			return copyArea(x, y, w, h, new Img(w,h), 0, 0);
		}
		if(x==0 && destX==0 && w==dest.getWidth() && w==this.getWidth()){
			if(destY < 0){
				/* negative destination y 
				 * need to shrink area by overlap and translate area origin */
				y -= destY;
				h += destY;
				destY = 0;
			}
			// limit area height to not exceed targets bounds
			h = Math.min(h, dest.getHeight()-destY);
			if(w > 0 && h > 0){
				System.arraycopy(this.getData(), y*w, dest.getData(), destY*w, w*h);
			}
		} else {
			if(destX < 0){
				/* negative destination x
				 * need to shrink area by overlap and translate area origin */
				x -= destX;
				w += destX;
				destX = 0;
			}
			if(destY < 0){
				/* negative destination y 
				 * need to shrink area by overlap and translate area origin */
				y -= destY;
				h += destY;
				destY = 0;
			}
			// limit area to not exceed targets bounds
			w = Math.min(w, dest.getWidth()-destX);
			h = Math.min(h, dest.getHeight()-destY);
			if(w > 0 && h > 0){
				for(int i = 0; i < h; i++){
					System.arraycopy(
							this.getData(), (y+i)*getWidth()+x, 
							dest.getData(), (destY+i)*dest.getWidth()+destX, 
							w);
				}
			}
		}
		return dest;
	}
	
	/**
	 * Sets value at the specified position.
	 * No bounds checks will be performed, positions outside of this
	 * images dimension can either result in a value for a different position
	 * or an ArrayIndexOutOfBoundsException.
	 * @param x
	 * @param y
	 * @param value
	 * @throws ArrayIndexOutOfBoundsException if resulting index from x and y
	 * is not within the data arrays bounds.
	 * @see #getValue(int, int)
	 */
	public void setValue(final int x, final int y, final int value){
		this.data[y*dimension.width + x] = value;
	}
	
	/**
	 * Fills the whole image with the specified value.
	 * @param value
	 */
	public void fill(final int value){
		Arrays.fill(getData(), value);
	}
	
	/**
	 * @return a deep copy of this Img.
	 */
	public Img copy(){
		return new Img(getDimension(), Arrays.copyOf(getData(), getData().length));
	}
	
	/**
	 * @return a BufferedImage of type INT_ARGB with this Imgs data copied to it.
	 * @see #toBufferedImage(BufferedImage)
	 * @see #getRemoteBufferedImage()
	 */
	public BufferedImage toBufferedImage(){
		BufferedImage img = BufferedImageFactory.getINT_ARGB(getDimension());
		return toBufferedImage(img);
	}
	
	/**
	 * Copies this Imgs data to the specified BufferedImage.
	 * @param img
	 * @return specified BufferedImage
	 * @throws ArrayIndexOutOfBoundsException if the provided BufferedImage
	 * has less values than this Img.
	 * @see #toBufferedImage()
	 * @see #getRemoteBufferedImage()
	 */
	public BufferedImage toBufferedImage(BufferedImage img){
		img.setRGB(0, 0, getWidth(), getHeight(), getData(), 0, getWidth());
		return img;
	}
	
	/**
	 * Creates a BufferedImage that shares the data of this Img. Changes in 
	 * this Img are reflected in the created BufferedImage and vice versa.
	 * The created BufferedImage uses an ARGB DirectColorModel with an 
	 * underlying DataBufferInt (similar to {@link BufferedImage#TYPE_INT_ARGB})
	 * @return BufferedImage sharing this Imgs data.
	 * @see #createRemoteImg(BufferedImage)
	 * @see #toBufferedImage()
	 */
	public BufferedImage getRemoteBufferedImage(){
		DirectColorModel cm = new DirectColorModel(32,
				0x00ff0000,       // Red
                0x0000ff00,       // Green
                0x000000ff,       // Blue
                0xff000000        // Alpha
                );
		DataBufferInt buffer = new DataBufferInt(getData(), numValues());
		WritableRaster raster = Raster.createPackedRaster(buffer, getWidth(), getHeight(), getWidth(), cm.getMasks(), null);
		BufferedImage bimg = new BufferedImage(cm, raster, false, null);
		return bimg;
	}
	
	/**
	 * Creates an Img sharing the specified BufferedImages data. Changes in 
	 * the BufferdImage are reflected in the created Img and vice versa.
	 * <p>
	 * Only BufferedImages with DataBuffer of {@link DataBuffer#TYPE_INT} can
	 * be used since the Img class uses an int[] to store its data. An
	 * IllegalArgumentException will be thrown if a BufferedImage with a 
	 * different DataBufferType is provided.
	 * @param bimg BufferedImage with TYPE_INT DataBuffer.
	 * @return Img sharing the BufferedImages data.
	 * @see #getRemoteBufferedImage()
	 * @see #Img(BufferedImage)
	 */
	public static Img createRemoteImg(BufferedImage bimg){
		int type = bimg.getRaster().getDataBuffer().getDataType();
		if(type != DataBuffer.TYPE_INT){
			throw new IllegalArgumentException(
					String.format("cannot create Img as remote of provided BufferedImage!%n"
							+ "Need BufferedImage with DataBuffer of type TYPE_INT (%d). Provided type: %d", 
							DataBuffer.TYPE_INT, type));
		}
		Img img = new Img(
				new Dimension(bimg.getWidth(),bimg.getHeight()), 
				((DataBufferInt)bimg.getRaster().getDataBuffer()).getData()
			);
		return img;
	}
	
	
	@Override
	public Iterator<Pixel> iterator() {
		Iterator<Pixel> pxIter = new Iterator<Pixel>() {
			Pixel px = new Pixel(Img.this, -1);
			
			@Override
			public Pixel next() {
				px.setIndex(px.getIndex()+1);
				return px;
			}
			
			@Override
			public boolean hasNext() {
				return px.getIndex()+1 < numValues();
			}
		};
		return pxIter;
	}
	
	@Override
	public Spliterator<Pixel> spliterator() {
		return new ImgSpliterator(0, numValues()-1);
	}
	
	/**
	 * {@link #forEach(Consumer)} method but with multithreaded execution.
	 * This Imgs {@link #spliterator()} is used to parallelize the workload.
	 * As the threaded execution comes with a certain overhead it is only
	 * suitable for more sophisticated consumer actions and large Images (1MP+) 
	 * @param action
	 * @see #forEach(Consumer)
	 */
	public void forEachParallel(final Consumer<? super Pixel> action) {
		ParallelForEachExecutor exec = new ParallelForEachExecutor(null, spliterator(), action);
		exec.invoke();
	}
	
	/**
	 * @see #forEachParallel(Consumer)
	 */
	@Override
	public void forEach(Consumer<? super Pixel> action) {
		Pixel p = getPixel();
		for(int i = 0; i < numValues(); p.setIndex(++i)){
			action.accept(p);
		}
	}
	
	/** default implementation of {@link Iterable#forEach(Consumer)} <br>
	 * only for performance test purposes as it is slower than the
	 * {@link Img#forEach(Consumer)} implementation
	 */
	void forEach_defaultimpl(Consumer<? super Pixel> action) {
		Iterable.super.forEach(action);
	}
	
	
	public static final int ch(final int color, final int startBit, final int numBits){
		return (color >> startBit) & ((1 << numBits)-1);
	}
	
	public static final int combineCh(int bitsPerChannel, int ... channels){
		int result = 0;
		int startBit = 0;
		for(int i = channels.length-1; i >= 0; i--){
			result |= channels[i] << startBit;
			startBit += bitsPerChannel;
		}
		return result;
	}
	
	public static final int a(final int color){
		return (color >> 24) & 0xff;
	}
	
	public static final int r(final int color){
		return (color >> 16) & 0xff;
	}
	
	public static final int g(final int color){
		return (color >> 8) & 0xff;
	}
	
	public static final int b(final int color){
		return (color) & 0xff;
	}
	
	public static final int argb_fast(final int a, final int r, final int g, final int b){
		return (a<<24)|(r<<16)|(g<<8)|b;
	}
	
	public static final int argb(final int a, final int r, final int g, final int b){
		return argb_fast(a & 0xff, r & 0xff, g & 0xff, b & 0xff);
	}
	
	public static final int argb_bounded(final int a, final int r, final int g, final int b){
		return argb_fast(
				a > 255 ? 255: a < 0 ? 0:a, 
				r > 255 ? 255: r < 0 ? 0:r, 
				g > 255 ? 255: g < 0 ? 0:g,
				b > 255 ? 255: b < 0 ? 0:b);
	}
	
	public static final int rgb_fast(final int r, final int g, final int b){
		return argb_fast(0xff, r, g, b);
	}
	
	public static final int rgb(final int r, final int g, final int b){
		return argb(0xff, r, g, b);
	}
	
	public static final int rgb_bounded(final int r, final int g, final int b){
		return argb_bounded(0xff, r, g, b);
	}
	
	public static final int getGrey(final int color, final int redWeight, final int greenWeight, final int blueWeight){
		return (r(color)*redWeight + g(color)*greenWeight + b(color)*blueWeight)/(redWeight+blueWeight+greenWeight);
	}
	
	public static final int getLuminance(final int color){
		return getGrey(color, 2126, 7152, 722);
	}
	
	
	
	private final class ImgSpliterator implements  Spliterator<Pixel> {
		
		final Pixel px;
		int endIndex;
		
		public ImgSpliterator(int startIndex, int endIndex) {
			px = new Pixel(Img.this, startIndex);
			this.endIndex = endIndex;
		}
		
		public void setEndIndex(int endIndex) {
			this.endIndex = endIndex;
		}

		@Override
		public boolean tryAdvance(final Consumer<? super Pixel> action) {
			if(px.getIndex() <= endIndex){
				int index = px.getIndex();
				action.accept(px);
				px.setIndex(index+1);
				return true;
			} else {
				return false;
			}
		}
		
		@Override
		public void forEachRemaining(final Consumer<? super Pixel> action) {
			int idx = px.getIndex();
			for(;idx <= endIndex; px.setIndex(++idx)){
				action.accept(px);
			}
		}

		@Override
		public Spliterator<Pixel> trySplit() {
			int currentIdx = Math.min(px.getIndex(), endIndex);
			int midIdx = currentIdx + (endIndex-currentIdx)/2;
			if(midIdx > currentIdx+1024){
				ImgSpliterator split = new ImgSpliterator(midIdx, endIndex);
				setEndIndex(midIdx-1);
				return split;
			} else {
				return null;
			}
		}

		@Override
		public long estimateSize() {
			int currentIndex = px.getIndex();
			int lastIndexPlusOne = endIndex+1;
			return lastIndexPlusOne-currentIndex;
		}

		@Override
		public int characteristics() {
			return NONNULL | SIZED | CONCURRENT | SUBSIZED;
		}
		
	}
	
	
	private final static class ParallelForEachExecutor extends CountedCompleter<Void> {
		private static final long serialVersionUID = 1L;
		
		final Spliterator<Pixel> spliterator;
		final Consumer<? super Pixel> action;
		
		ParallelForEachExecutor(
				ParallelForEachExecutor parent, 
				Spliterator<Pixel> spliterator,
				Consumer<? super Pixel> action) 
		{
			super(parent);
			this.spliterator = spliterator; 
			this.action = action;
		}

		public void compute() {
			Spliterator<Pixel> sub;
			while ((sub = spliterator.trySplit()) != null) {
				addToPendingCount(1);
				new ParallelForEachExecutor(this, sub, action).fork();
			}
			spliterator.forEachRemaining(action);
			propagateCompletion();
		}
	}
}
