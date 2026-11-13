package com.google.gson;
import java.io.Reader;
/** Compile-check stub. */
public final class Gson {
	Gson() {}
	public <T> T fromJson(Reader json, Class<T> classOfT) throws JsonParseException {
		throw new UnsupportedOperationException("stub");
	}
	public void toJson(Object src, Appendable writer) throws JsonParseException {
		try { writer.append("{}"); } catch (java.io.IOException e) { throw new JsonParseException(e.getMessage()); }
	}
}
