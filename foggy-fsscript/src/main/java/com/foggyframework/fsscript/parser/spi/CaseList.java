package com.foggyframework.fsscript.parser.spi;


import com.foggyframework.fsscript.exp.CaseExp;

import java.util.ArrayList;

public class CaseList extends ArrayList<CaseExp> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	Exp caseDefault;

	public Exp getCaseDefault() {
		return caseDefault;
	}

	public void setCaseDefault(Exp caseDefault) {
		this.caseDefault = caseDefault;
	}

}
