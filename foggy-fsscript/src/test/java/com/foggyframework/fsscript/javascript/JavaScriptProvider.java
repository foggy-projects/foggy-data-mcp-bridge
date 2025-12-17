package com.foggyframework.fsscript.javascript;

import javax.script.*;
import java.io.FileNotFoundException;
import java.io.FileReader;

/**
 * @author lihao
 * @create 2022-08-30 9:47
 * @desc
 **/
public class JavaScriptProvider<T> {

    public T loadJs(String jsName,Class<T> clazz) throws FileNotFoundException, ScriptException {
        //创建一个脚本引擎管理器
        ScriptEngineManager manager = new ScriptEngineManager();
        //获取一个指定的名称的脚本管理器
        ScriptEngine engine = manager.getEngineByName("javascript");
        //获取js文件所在的目录的路径
        String path = "/Users/fengjianguang/workspaces/v3-foggy/work/foggy-framework-fsscript/src/test/java/com/foggyframework/fsscript/javascript/";
        //engine.eval(new FileReader(path+jsName+".js"));
        engine.eval(new FileReader(jsName));
        //从脚本引擎中返回一个给定接口的实现
        Invocable invocable = (Invocable) engine;
        return invocable.getInterface(clazz);
    }


    public static void main(String[] args) {
        try {
            JavaScriptProvider<JSMethods> jsProvider = new JavaScriptProvider<>();

            JSMethods jsMethods = jsProvider.loadJs("/Users/fengjianguang/workspaces/v3-foggy/work/foggy-framework-fsscript/src/test/java/com/foggyframework/fsscript/javascript/test.js",JSMethods.class);

            System.out.println(jsMethods.encodeInp("123"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (ScriptException e) {
            e.printStackTrace();
        }

    }

}
