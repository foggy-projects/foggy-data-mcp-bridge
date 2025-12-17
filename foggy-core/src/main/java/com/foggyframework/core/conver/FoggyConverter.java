package com.foggyframework.core.conver;

import org.springframework.core.convert.converter.Converter;

import java.util.Date;

public interface FoggyConverter<T,X> extends  Converter<T, X> {
}
