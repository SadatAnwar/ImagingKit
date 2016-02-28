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

/**
 * Image class with data stored in an int array. 
 * <p>
 * In contrast to {@link BufferedImage} the Img class only offers
 * pixel data to be stored as integer values simplifying data retrieval
 * and increasing performance due to less overhead and omitting color
 * model conversions. <br>
 * However the Img class can be easily used together with BufferedImages
 * offering convenience methods like {@link #Img(BufferedImage)},
 * {@link #toBufferedImage()} or {@link #createRemoteImg(BufferedImage)}.
 * <p>
 * Moreover the Img class targets lambda expressions introduced in Java 8 
 * useful for per pixel operations by implementing the {@link Iterable} 
 * interface and providing {@link #iterator()}, {@link #spliterator()}, 
 * {@link #forEach(Consumer)} and {@link #forEachParallel(Consumer)}.
 * 
 * @author hageldave
 */
public class Img implements Iterable<Pixel> {
	
	/** boundary mode that will return 0 for out of bounds positions.
	 * @see #getValue(int, int, int) */
	public static final int boundary_mode_zero = 0;
	
	/** boundary mode that will repeat the the edge of of an Img for out of 
	 * bounds positions.
	 * @see #getValue(int, int, int) */
	public static final int boundary_mode_repeat_edge = 1;
	
	/** boundary mode that will repeat the Img for out of bounds positions.
	 * @see #getValue(int, int, int) */
	public static final int boundary_mode_repeat_image = 2;
	
	/** boundary mode that will mirror the Img for out of bounds positions
	 * @see #getValue(int, int, int) */
	public static final int boundary_mode_mirror = 3;
	
	
	/** data array of this Img containing a value for each pixel in row major order */
	private final int[] data;
	
	/** dimension of this Img */
	private final Dimension dimension;
	
	
	/**
	 * Creates a new Img of specified dimensions.
	 * Values are initialized to 0.
	 * @param width
	 * @param height
	 */
	public Img(int width, int height){
		this(new Dimension(width, height));
	}
	
	/**
	 * Creates a new Img of specified Dimension.
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
	 * Creates a new Img of specified dimensions.
	 * Provided data array will be used as this images data.
	 * @param width
	 * @param height
	 * @param data
	 */
	public Img(int width, int height, int[] data){
		this(new Dimension(width, height), data);
	}
	
	/**
	 * Creates a new Img of specified dimensions.
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
	 * @see #getPixel(int, int)
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
		return Pixel.argb_fast/*_bounded*/(
				blend( blend(Pixel.a(c00), Pixel.a(c01), mx), blend(Pixel.a(c10), Pixel.a(c11), mx), my),
				blend( blend(Pixel.r(c00), Pixel.r(c01), mx), blend(Pixel.r(c10), Pixel.r(c11), mx), my),
				blend( blend(Pixel.g(c00), Pixel.g(c01), mx), blend(Pixel.g(c10), Pixel.g(c11), mx), my),
				blend( blend(Pixel.b(c00), Pixel.b(c01), mx), blend(Pixel.b(c10), Pixel.b(c11), mx), my) );
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
	 * No bounds checks are performed for x and y.
	 * <p>
	 * <b>Tip:</b><br>
	 * Do not use this method repeatedly while iterating the image.
	 * Use {@link Pixel#setPosition(int, int)} instead to avoid excessive
	 * allocation of Pixel objects.
	 * <p>
	 * You can also use <code>for(Pixel px: img){...}</code> syntax or the 
	 * {@link #forEach(Consumer)} method to iterate this image.
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
			
			@Override
			public void forEachRemaining(Consumer<? super Pixel> action) {
				px.setIndex(px.getIndex()+1);
				for(int i = px.getIndex(); i < Img.this.numValues(); px.setIndex(++i)){
					action.accept(px);
				}
			}
		};
		return pxIter;
	}
	
	@Override
	public Spliterator<Pixel> spliterator() {
		return new ImgSpliterator(0, numValues()-1);
	}
	
	// TODO: javadoc
	public Spliterator<Pixel> spliterator(int x, int y, int width, int height) {
		if(		width <= 0 || height <= 0 || 
				x < 0 || y < 0 ||
				x+width > getWidth() || y+height > getHeight() )
		{
			throw new IllegalArgumentException(String.format(
							"provided area [%d,%d][%d,%d] is not within bounds of the image [%d,%d]", 
							x,y,width,height, getWidth(), getHeight()));
		}
		return new ImgAreaSpliterator(x,y,width,height);
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
	
	// TODO: javadoc
	public void forEachParallel(int xStart, int yStart, int width, int height, final Consumer<? super Pixel> action) {
		ParallelForEachExecutor exec = new ParallelForEachExecutor(null, spliterator(xStart, yStart, width, height), action);
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
	
	// TODO: javadoc
	public void forEach(int xStart, int yStart, int width, int height, Consumer<? super Pixel> action) {
		Pixel p = getPixel(xStart, yStart);
		int numValues = width*height;
		for(int i = 0; i < numValues; ++i, p.setPosition(xStart+i%width, yStart+i/width)){
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
	
	/**
	 * Spliterator class for Img bound to a specific area
	 * @author hageldave
	 * @since 1.1
	 */
	private final class ImgAreaSpliterator implements Spliterator<Pixel> {
		
		final Pixel px;
		final int startX, startY, width, height;
		int areaEndIndex;
		
		public ImgAreaSpliterator(int xStart, int yStart, int width, int height){
			this(xStart,yStart,width,height, 0,width*height-1);
		}
		
		public ImgAreaSpliterator(int xStart, int yStart, int width, int height, int areaStartIndex, int areaEndIndex){
			this.startX = xStart;
			this.startY = yStart;
			this.width = width;
			this.height = height;
			
			Img img = Img.this;
			int areaX = areaStartIndex%width;
			int areaY = areaStartIndex/width;
			this.px = new Pixel(img, img.getWidth()*(startY+areaY)+startX+areaX);
			this.areaEndIndex = areaEndIndex;
		}
		
		public void setEndIndex(int endIndex) {
			this.areaEndIndex = endIndex;
		}
		
		private int getAreaIdx(){
			return (px.getY()-startY)*width+px.getX()-startX;
		}
		
		@Override
		public boolean tryAdvance(final Consumer<? super Pixel> action) {
			int areaIDX = getAreaIdx();
			if(areaIDX <= areaEndIndex){
				action.accept(px);
				areaIDX++;
				px.setPosition(startX+areaIDX%width, startY+areaIDX/width);
				return true;
			} else {
				return false;
			}
		}
		
		@Override
		public void forEachRemaining(final Consumer<? super Pixel> action) {
			int areaIDX = getAreaIdx();
			for(;areaIDX <= areaEndIndex; ++areaIDX, px.setPosition(startX+areaIDX%width, startY+areaIDX/width)){
				action.accept(px);
			}
		}
		
		@Override
		public Spliterator<Pixel> trySplit() {
			int currentIdx = Math.min(getAreaIdx(), areaEndIndex);
			int midIdx = currentIdx + (areaEndIndex-currentIdx)/2;
			if(midIdx > currentIdx+1024){
				ImgAreaSpliterator split = new ImgAreaSpliterator(startX, startY, width, height, midIdx, areaEndIndex);
				setEndIndex(midIdx-1);
				return split;
			} else {
				return null;
			}
		}
		
		@Override
		public long estimateSize() {
			int currentIndex = getAreaIdx();
			int lastIndexPlusOne = areaEndIndex+1;
			return lastIndexPlusOne-currentIndex;
		}
		
		@Override
		public int characteristics() {
			return NONNULL | SIZED | CONCURRENT | SUBSIZED;
		}
		
	}
	
	/**
	 * Spliterator class for Img
	 * @author hageldave
	 */
	private final class ImgSpliterator implements Spliterator<Pixel> {
		
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
	
	/**
	 * CountedCompleter class for multithreaded execution of a Consumer on a
	 * Pixel Spliterator. Used to realise multithreaded forEach loop.
	 * @author hageldave
	 * @see Img#forEachParallel(Consumer)
	 */
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
