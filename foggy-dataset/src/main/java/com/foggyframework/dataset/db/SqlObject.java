package com.foggyframework.dataset.db;


import com.foggyframework.dataset.db.dialect.FDialect;
import lombok.Data;

@Data
public abstract class SqlObject {

	protected String name;

	protected String caption;

	protected String comment;

	protected boolean quoted = false;

	public SqlObject() {
		super();
	}

	public SqlObject(String name) {
		super();
		this.name = name;
	}

	public SqlObject(String name, String caption) {
		super();
		this.name = name;
		this.caption = caption;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SqlObject other = (SqlObject) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (getDbObjectType() != other.getDbObjectType())
			return false;
		return true;
	}

	public abstract DbObjectType getDbObjectType();

	public String getQuotedName(FDialect dialect) {
		return quoted ? dialect.openQuote() + name + dialect.closeQuote() : name;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((getDbObjectType() == null) ? 0 : getDbObjectType().hashCode());
		return result;
	}

}
