package hageldave.imagingkit.core;

import static hageldave.imagingkit.core.Pixel.*;

import java.util.function.Consumer;

import hageldave.imagingkit.core.Img;

/**
 * This Enum class provides a variation of different blend functions to
 * blend a bottom and top value (pixels). It also provides methods to calculate
 * the blend of two colors with additional transparency specification 
 * (see {@link #blend(int, int, float, Blending)}), as well as ready to use 
 * Consumers for blending two {@link Img}s 
 * (see {@link #getAlphaBlendingWith(Img, float)}).
 * <p>
 * Here's a short example on how to blend two images using the Blending class:
 * <pre>
 * {@code
 * Img bottom, top;
 * ... initialize images ...
 * bottom.forEach(Blending.AVERAGE.getBlendingWith(top));
 * }</pre>
 * You can also specify the offset of the top image on the bottom image:
 * <pre>
 * {@code
 * Img bottom, top;
 * ... initialize images ...
 * int x = 3;  // horizontal offset
 * int y = -5; // vertical offset 
 * bottom.forEach(Blending.DIFFERENCE.getBlendingWith(top, x, y));
 * }</pre>
 * If you do not rely on the Img class or only need to blend two single colors
 * you may omit this level of abstraction and use the per color or 
 * channel methods:
 * <pre>
 * {@code
 * int bottomARGB = 0x8844FF11;
 * int topARGB =    0xFF118899;
 * float opacity = 0.7f;
 * int blendARGB = Blending.blend(bottomARGB, topARGB, opacity, Blending.DODGE);
 * 	
 * int channel1 = 0xBB;
 * int channel2 = 0x7F;
 * int channelBlend = Blending.SCREEN.blendFunction.blend(channel1, channel2);
 * }</pre>
 * 
 * 
 * @author hageldave
 * @since 1.3
 */
public enum Blending {
	/** Blend function: f(a,b)=b 										*/
	NORMAL(		(a,b) -> b ),
	
	/** Blend function: f(a,b)=(a+b)/2 									*/
	AVERAGE( 	(a,b) -> ((a+b)>>1) ),
	
	/** Blend function: f(a,b)=a*b <br> a,b in [0,1] 					*/
	MULTIPLY( 	(a,b) -> ((a*b)>>8) ),
	
	/** Blend function: f(a,b) = 1 - (1-a) * (1-b) <br> a,b in [0,1] 	*/
	SCREEN( 	(a,b) -> (0xff - ((0xff-a) * (0xff-b)>>8)) ),
	
	/** Blend function: f(a,b) = min(a,b) 								*/
	DARKEN( 	(a,b) -> Math.min(a, b) ),
	
	/** Blend function: f(a,b) = max(a,b) 								*/
	BRIGHTEN(	(a,b) -> Math.max(a, b) ),
	
	/** Blend function: f(a,b) = |a-b|	 								*/
	DIFFERENCE( (a,b) -> Math.abs(a-b) ),
	
	/** Blend function: f(a,b) = a+b		 								*/
	ADDITION(	(a,b) -> Math.min(a+b, 0xff) ),
	
	/** Blend function: f(a,b) = a + b - 1 <br> a,b in [0,1]	 			*/
	SUBTRACTION((a,b) -> Math.max(a+b-0xff, 0) ),
	
	/** Blend function: f(a,b) = a<sup>2</sup>/(1-b) <br> a,b in [0,1]	*/
	REFLECT(	(a,b) -> Math.min(a*a / Math.max(0xff-b, 1), 0xff) ),
	
	/** Blend function:<pre>
	 * f(a,b) = 2*a*b            for a < 1/2
	 * f(a,b) = 1-2*(1-a)*(1-b)  else
	 * a,b in [0,1]
	 * </pre>																*/
	OVERLAY(	(a,b) -> a < 128 ? (a*b)>>7 : 0xff-(((0xff-a)*(0xff-b))>>7) ),
	
	/** Blend function:<pre>
	 * f(a,b) = 2*a*b            for b < 1/2
	 * f(a,b) = 1-2*(1-a)*(1-b)  else
	 * a,b in [0,1]
	 * </pre>																*/
	HARDLIGHT(	(a,b) -> b < 128 ? (a*b)>>7 : 0xff-(((0xff-a)*(0xff-b))>>7) ),
	
	/** Blend function: (1-a)*multiply(a,b)+a*screen(a,b)<br>a,b in [0,1]*/
	SOFTLIGHT(	(a,b) -> {int c=(a*b)>>8; return c + (a*(0xff-(((0xff-a)*(0xff-b)) >> 8)-c) >> 8);} ),
	
	/** Blend function: a/(1-b) <br> a,b in [0,1] 						*/
	DODGE(		(a,b) -> Math.min((a<<8)/Math.max(0xff-b, 1),0xff) )
	;
		
	////// ATTRIBUTES / METHODS //////
	
	/** This blending's blend function */
	public final BlendFunction blendFunction;
	
	/** Enum Constructor */
	private Blending(BlendFunction func) {
		this.blendFunction = func;
	}

	/**
	 * Returns the {@code Consumer<Pixel>} for blending with the specified top 
	 * Img. The top image will be set off to the specified point on the target.
	 * The specified opacity defines how strongly the blended color will occlude
	 * the bottom color (default value is 1 for complete occlusion).
	 * <br><b>
	 * This consumer will apply an ARGB blending, respecting the pixels alpha
	 * (opacity) values. </b> 
	 * This means that not only the specified blend opacity is considered but 
	 * also the individual opacity of the pixel. E.g. if the blend opacity
	 * is 0.5 and the top pixel's opacity is also 0.5 (a=128) the effective
	 * occlusion strength of the blend is 0.5*0.5=0.25 and the bottom color will 
	 * "shine through" the blend influencing the final color by a factor of 0.75.
	 * See also the RGB blending version without opacity consideration 
	 * {@link #getBlendingWith(Img, int, int)}.
	 * 
	 * @param topImg top image of the blending (consumer is applied to bottom)
	 * @param xTopOffset horizontal offset of the top image on the bottom
	 * @param yTopOffset vertical offset of the top image on the bottom
	 * @param opacity of the blended color over the bottom color
	 * @return Consumer to apply to bottom Img that will perform the specified
	 * blending. {@code bottomImg.forEach(blendingConsumer);}
	 * 
	 * @see #getAlphaBlendingWith(Img, float)
	 * @see #getBlendingWith(Img, int, int)
	 * @see #getBlendingWith(Img)
	 */
	public Consumer<Pixel> getAlphaBlendingWith(Img topImg, int xTopOffset, int yTopOffset, float opacity){
		return blending(topImg, xTopOffset, yTopOffset, opacity, blendFunction);
	}

	/** RGB blending with opaque result (alpha is ignored) */
	public Consumer<Pixel> getBlendingWith(Img topImg, int xTop, int yTop){
		return blending(topImg, xTop, yTop, blendFunction);
	}

	/**
	 * Returns the {@code Consumer<Pixel>} for blending with the specified top 
	 * Img. The top image will have no offset on the bottom image (starting at (0,0)).
	 * The specified opacity defines how strongly the blended color will occlude
	 * the bottom color (default value is 1 for complete occlusion).
	 * <br><b>
	 * This consumer will apply an ARGB blending, respecting the pixels alpha
	 * (opacity) values. </b> 
	 * This means that not only the specified blend opacity is considered but 
	 * also the individual opacity of the pixel. E.g. if the blend opacity
	 * is 0.5 and the top pixel's opacity is also 0.5 (a=128) the effective
	 * occlusion strength of the blend is 0.5*0.5=0.25 and the bottom color will 
	 * "shine through" the blend influencing the final color by a factor of 0.75.
	 * See also the RGB blending version without opacity consideration 
	 * {@link #getBlendingWith(Img, int, int)}.
	 * @param topImg top image of the blending (consumer is applied to bottom)
	 * @param opacity of the blended color over the bottom color
	 * @return Consumer to apply to bottom Img that will perform the specified
	 * blending. {@code bottomImg.forEach(blendingConsumer);}
	 */
	public Consumer<Pixel> getAlphaBlendingWith(Img topImg, float opacity){
		return getAlphaBlendingWith(topImg,0,0, opacity);
	}

	public Consumer<Pixel> getBlendingWith(Img topImg){
		return getBlendingWith(topImg, 0, 0);
	}
		
	
	////// STATIC //////
	
	/** Interface providing the {@link #blend(int, int)} method */
	public static interface BlendFunction {
		/**
		 * Calculates the blended value of two 8bit values 
		 * (e.g. red, green or blue channel values).
		 * The resulting value is also 8bit.
		 * @param bottom value
		 * @param top value
		 * @return blended value
		 */
		public int blend(int bottom, int top);
	}
	
	public static int blend(int bottomRGB, int topRGB, BlendFunction func){
		return rgb_fast(func.blend(r(bottomRGB), r(topRGB)), 
						func.blend(g(bottomRGB), g(topRGB)),
						func.blend(b(bottomRGB), b(topRGB)));
	}
	
	public static int blend(int bottomRGB, int topRGB, Blending mode){
		return blend(bottomRGB, topRGB, mode.blendFunction);
	}

	public static int blend(int bottomARGB, int topARGB, float opacity, BlendFunction func){
		int temp = 0;
		
		float a = Math.min(opacity*(temp=a(topARGB)) + a(bottomARGB),0xff);
		
		opacity *= (temp/255.0f);
		float transparency = 1-opacity;
		
		float r = opacity*func.blend(temp=r(bottomARGB), r(topARGB)) + temp*transparency;
		float g = opacity*func.blend(temp=g(bottomARGB), g(topARGB)) + temp*transparency;
		float b = opacity*func.blend(temp=b(bottomARGB), b(topARGB)) + temp*transparency;
		
		return argb_fast((int)a, (int)r, (int)g, (int)b);
	}
	
	public static int blend(int bottomARGB, int topARGB, float opacity, Blending mode){
		return blend(bottomARGB, topARGB, opacity, mode.blendFunction);
	}
	
	public static Consumer<Pixel> blending(Img top, int xTop, int yTop, float opacity, BlendFunction func){
		return (px)->
		{
			int x = px.getX()-xTop;
			int y = px.getY()-yTop;
			
			if(x >= 0 && y >= 0 && x < top.getWidth() && y < top.getHeight()){
				px.setValue(blend(px.getValue(), top.getValue(x, y), opacity, func));
			}
		};
	}
	
	public static Consumer<Pixel> blending(Img top, int xTop, int yTop, BlendFunction func){
		return (px)->
		{
			int x = px.getX()-xTop;
			int y = px.getY()-yTop;
			
			if(x >= 0 && y >= 0 && x < top.getWidth() && y < top.getHeight()){
				px.setValue(blend(px.getValue(), top.getValue(x, y), func));
			}
		};
	}
	
}