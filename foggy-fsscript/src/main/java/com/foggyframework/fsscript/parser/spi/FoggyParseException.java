package com.foggyframework.fsscript.parser.spi;


import com.foggyframework.fsscript.parser.ParseRegion;

public class FoggyParseException extends RuntimeException {
    /**
     *
     */
    private static final long serialVersionUID = -5384669803436434220L;
    private final ParseRegion region;

    /**
     * Creates an MdxParseException with a region of the source code and a
     * specified detail message.
     *
     * @param region  Region of source code which contains the error
     * @param message the detail message. The detail message is saved for later
     *                retrieval by the {@link #getMessage()} method.
     */
    public FoggyParseException(String exp, ParseRegion region, String message) {
        super("Parse expression [" + exp + "] has error ,msg : " + message);
        this.region = region;
    }


    public ParseRegion getRegion() {
        return region;
    }
}
