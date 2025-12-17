package com.foggyframework.dataset.model.support;

import com.foggyframework.fsscript.parser.spi.Exp;
import lombok.Data;

@Data
public class ResultSetModelSupport {

    protected String name;

    protected Exp onStartBuild;

}
