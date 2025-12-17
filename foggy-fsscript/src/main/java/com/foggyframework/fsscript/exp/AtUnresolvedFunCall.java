package com.foggyframework.fsscript.exp;



import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.ExpFactory;

import java.util.List;

/**
 * 通过@actionName    来调用行为
 */
public class AtUnresolvedFunCall extends AbstractExp<String> {
    /**
     *
     */
    private static final long serialVersionUID = 7175513640522546982L;
    List<Exp> args;
    transient ExpFactory expFactory;


    public AtUnresolvedFunCall(ExpFactory expFactory, final String name, final List<Exp> args) {
        super(name);
        this.args = args;
        this.expFactory = expFactory;
    }

    @Override
    public Object evalValue(final ExpEvaluator context)
            {

        Object[] as = new Object[args.size()];
        int i = 0;
        for (Exp e : args) {
            as[i] = e.evalResult(context);
            i++;
        }
        throw new UnsupportedOperationException();
////		Object obj = fd.execute(context, as);
//        ActionClientManagerImpl.CacheActionClient.CacheAction ca = ActionClientManagerImpl.getInstance().getMethodName2CacheAction(this.value);
//        if (ca != null) {
//            return ca.process(this.value, as);
//        }
//        Object obj = ActionManagerSupport._doAction2(this.value, as);
//        return obj;
        // return "\n[#error in execute :" + value + " args:" + args +
        // "]\n";
    }

    public List<Exp> getArgs() {
        return args;
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        throw new UnsupportedOperationException();
    }


    public void setArgs(List<Exp> args) {
        this.args = args;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Object o : args) {
            if (o != null) {
                sb.append(o.toString());
            }
            sb.append(",");
        }
        return "[UnresolvedFunCall : " + value + ",args:(" + sb.toString() + ")]";
    }
}
