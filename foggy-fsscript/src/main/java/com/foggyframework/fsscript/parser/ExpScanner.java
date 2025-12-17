/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript.parser;

import java_cup.runtime.Symbol;

import java.io.IOException;

public class ExpScanner extends ElExpScanner {

	public static void main(final String[] args) {
		// String x = "xxxx #io(df,wer)asdf (ewqrwer";

//		final ExpParser p = new ExpParser((ExpFactory) null);
//		try {
			// final Exp o = (Exp)
			// p.parseInternal("ds1.sum(MAP_LEVEL,MARKER_TYPE like '%0%' and
			// MARKER_FILTER='syr')");
			// Exp o = p.compileEl("function w(x){} ; function w(x){}");
			// Exp o = p.compileEl("if(auctId){"+
			// "var auct = auctionServiceImpl.getObject(auctId);"+
			// "var merch = merchServiceImpl.getObject(auct.merchId);"+
			// "}else if(merchId){"+
			// "var merch = merchServiceImpl.getObject(merchId);"+
			// "var auct = auctionServiceImpl.getLastAuctByMerchId(merchId);"+
			// "var auctId = auct.id;"+
			// "}");
//			String str = FileUtils.toString(new File(
//					"/Users/fengjianguang/workspaces/foggy-workspace/framework.core/foggy-frame-core/resources/xx"));
//			Exp to1 = p.compile(str);
			// Exp to = p.compileEl("uuid()");
			// Exp o = p.compile("${1}d${x}");
			// Exp x = p.compile("${var x=1;return x;}");
			// Exp r = p.compile("1");
			// Exp r1 = p.compile("${1}");
			// // Systemx.out.println(x.evalResult(new DefaultExpEvaluator()));
			// Exp o = p.compile(FileUtils.toString(new File(ExpScanner.class
			// .getResource("/xx").getFile())));
			// p.compile("esf");
			// o=p.compile("1${d}sdf");
			// p.compile("func(${d+fun()})");
			// // Systemx.out.println(o);
//			System.out.println(to1);
//		} catch (final Exception e) {
//
//			e.printStackTrace();
//		}
	}


	int dollarDeep = 0;

	boolean inDollar = false;

	public ExpScanner(String vmdx) {
		super(vmdx);
	}

	@Override
	protected Symbol doLBRACE() throws IOException {

		advance();
		dollarDeep++;

		return makeToken(ExpSymbols.LBRACE, "{");
	}

	@Override
	protected Symbol doRBRACE() throws IOException {
		advance();

		dollarDeep--;
		if (dollarDeep == 0) {
			inDollar = false;
		}

		return makeToken(ExpSymbols.RBRACE, "}");
	}

	@Override
	public Symbol next_token1() throws Exception {
		StringBuilder id ;

		if (nextChar == -1) {
			return makeToken(ExpSymbols.EOF, "EOF");
		}

		if (!inDollar) {
			id = new StringBuilder();
			while (true) {
				if (nextChar == '$' && next() == '{') {
					// 鐩村埌鍑虹幇'${'
					inDollar = true;
					return makeString(id.toString());
				}
				id.append((char) nextChar);
				advance();
				if (nextChar == -1) {
					return makeString(id.toString());
				}
			}
		}

		if (nextChar == '$' && next() == '{') {
			advance();
			advance();
			dollarDeep++;
			return makeSymbol(ExpSymbols.DOLLAR_LBRACE, "${");
		}
		return super.next_token1();
	}
}