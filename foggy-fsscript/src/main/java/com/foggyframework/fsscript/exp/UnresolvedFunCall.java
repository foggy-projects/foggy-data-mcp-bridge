package com.foggyframework.fsscript.exp;


import com.foggyframework.core.ex.RX;
import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.ExpFactory;

import java.io.ObjectInputStream;
import java.util.List;
import java.util.function.Function;

public class UnresolvedFunCall extends AbstractExp<String> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 7175513640522546982L;
	List<Exp> args;
	transient ExpFactory expFactory;

	public UnresolvedFunCall(ExpFactory expFactory, final Exp name, final List<Exp> args) {
		this(expFactory, (String) ((AbstractExp<?>) name).value, args);
	}

	public UnresolvedFunCall(ExpFactory expFactory, final String name, final List<Exp> args) {
		super(name);
		this.args = args;
		this.expFactory = expFactory;
	}

	@Override
	public Object evalValue(final ExpEvaluator context)
			{
		Object cc = context.getVar(value);

		if (cc instanceof Function) {
			// 采用内置函数执行
			Object[] ccArgs = new Object[args.size()];
			int i = 0;
			for (Exp exp : args) {
				ccArgs[i] = exp.evalResult(context);
				i++;
			}
			return ((Function) cc).apply(ccArgs);
		}
		FunDef fd = expFactory.getFunctionSet().getFun(this);
		if (fd == null) {
			if(cc instanceof FunDef){
				fd = (FunDef) cc;
			}
		}
		if (fd==null){
			RX.notNull(fd, "未能找到函数 ["+ this.value+ "]!");
		}

		Exp[] as = new Exp[args.size()];
		args.toArray(as);
		Object obj = fd.execute(context, as);
		return obj;
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

	private void readObject(ObjectInputStream in) {
//		try {
//			in.defaultReadObject();
//			expFactory = ExpFactory.DEFAULT; // TODO
//												// expFactory应使用某种方式，使之与writeObject时一致，不过目前系统中只有一个ExpFactory
//			// // Systemx.out.println(in.readObject());
//		} catch (ClassNotFoundException | IOException e) {
//			e.printStackTrace();
//		}

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
