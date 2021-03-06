package hageldave.imagingkit.filter.settings;

public interface ValueConstraint {

	public boolean isValuePermitted(Object val);
	
	public default void throwIfValueNotPermitted(Object val) throws IllegalArgumentException {
		if(!isValuePermitted(val))
			throw new IllegalArgumentException(String.format("Provided value %s does not meet value constraint!", val));
	}
	
	
	public static final ValueConstraint ALLOWALLBUTNULL = new ValueConstraint() {
		@Override
		public boolean isValuePermitted(Object val) {
			return val != null;
		}
	};
}
