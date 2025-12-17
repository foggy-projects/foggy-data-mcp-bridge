package com.foggyframework.fsscript.parser.spi;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class ElementItem {

	Node element;

	Object object;

	public ElementItem(Node element, Object object) {
		super();
		this.element = element;
		this.object = object;
	}

	public String getAttribute(String name) {
		return element instanceof Element ? ((Element) element).getAttribute(name) : null;
	}

	public Node getElement() {
		return element;
	}

	public Object getObject() {
		return object;
	}

	public boolean hasAttribute(String name) {
		return element instanceof Element ? ((Element) element).hasAttribute(name) : false;
	}

	public boolean isElement() {
		return element instanceof Element;
	}

	public void setAttribute(String name, String v) {
		((Element) element).setAttribute(name, v);
	}

	public void setElement(Element element) {
		this.element = element;
	}

	public void setObject(Object object) {
		this.object = object;
	}
}
