package com.foggyframework.fsscript.exp;

public class ThrowException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1492507249630546980L;

	Object errorObject;

	public ThrowException() {
		super();
	}

	public ThrowException(Object errorObject) {
		super(errorObject==null?"":errorObject.toString());
		this.errorObject = errorObject;
	}

	public ThrowException(String message) {
		super(message);
	}
	// public ThrowException(String message, Throwable cause) {
	// super(0,message, cause);
	// }
	//
	// public ThrowException(Throwable cause) {
	// super(cause);
	// }

	public Object getErrorObject() {
		return errorObject;
	}

}
