package hageldave.imagingkit.core;

import static org.junit.Assert.fail;

public class JunitUtils {

	
	public static void testException(Runnable codeThatThrows, Class<? extends Throwable> exClass){
		boolean wasThrown = true;
		try{
			codeThatThrows.run();
			wasThrown = false;
		} catch(Throwable t){
			if(!exClass.isInstance(t)){
				fail(String.format("Expected Exception %s but got %s", exClass, t.getClass()));
			}
		}
		if(!wasThrown){
			fail(String.format("Expected Exception %s but none was thrown",exClass));
		}
	}
	
}
