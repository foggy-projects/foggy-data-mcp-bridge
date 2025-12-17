package com.foggyframework.fsscript.fun;


import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;

@Slf4j
public class ParseFile implements FunDef {

    @Override
    public Object execute(ExpEvaluator ee, Exp[] args) {


        Object v = args[0].evalResult(ee);
       if(StringUtils.isEmpty(v)){
           return null;
       } if (v instanceof String) {
            return new File((String) v);
        } else if (v instanceof File) {
            return v;
        } else if (v instanceof Resource) {
            try {
                return ((Resource) v).getFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        log.error("不支持类型【" + v + "】的ParseFile."+ args[0]);
        throw new UnsupportedOperationException("不支持类型【" + v + "】的ParseFile." + args[0]);
    }

    @Override
    public String getName() {
        return "parseFile,toFile,file";
    }

}
