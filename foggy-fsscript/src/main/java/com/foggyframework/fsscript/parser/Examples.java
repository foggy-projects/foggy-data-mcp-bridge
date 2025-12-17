/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript.parser;

//@RunWith(SpringJUnit4ClassRunner.class)
//@ContextConfiguration({
//		"classpath*:spring\\*.xml"})
//@TransactionConfiguration(transactionManager = "metaTransactionManager", defaultRollback = true)
//
public class Examples {
	public static void main(String[] args) {

		String str = "/static/jsClasses/WeiXin.js${1}";

		Object obj = new ExpParser().compile(str);
		System.out.println(obj);
	}
	// @Inject
	// @Qualifier("jdbcTemplate4ods")
	// JdbcTemplate jdbcTemplate;
	//
	// @Rollback(true)
	//
	// @Test
	// public void test() {
	//
	// ExpFactory.DEFAULT.getFunctionSet().append(new PLUS());
	// ExpFactory.DEFAULT.getFunctionSet().append(new Eq());
	// ExpFactory.DEFAULT.getFunctionSet().append(new And());
	// ExpFactory.DEFAULT.getFunctionSet().append(new SUM());
	//
	// final DataSetExpParser p = new DataSetExpParser((ExpFactory) null);
	//
	// try {
	// final Exp o = (Exp)
	// p.compile("ds1.sum(THISCREDITBALA,BCHCD='10001' and CURRNO='01')");
	// // Systemx.out.println(o);
	// // Systemx.out.println("value :"+o.evalResult(new DefaultExpEvaluator()));
	//
	// } catch (final Exception e) {
	// // TODO Auto-generated catch block
	// e.printStackTrace();
	// }
	// }
	// public static void main(String[] args) {
	// final DataSetExpParser p = new DataSetExpParser((ExpFactory) null);
	// }

}
