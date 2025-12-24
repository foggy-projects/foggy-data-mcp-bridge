package com.foggyframework.dataset.client;

import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.dataset.client.proxy.ReturnConverterManagerImpl;
import com.foggyframework.dataset.client.proxy.converter.ListReturnConverter;
import com.foggyframework.dataset.client.proxy.converter.MapReturnConverter;
import com.foggyframework.dataset.client.proxy.converter.PagingReturnConverter;
import com.foggyframework.dataset.client.proxy.converter.SimpleObjectReturnConverter;
import com.foggyframework.dataset.client.proxy.converter.list.*;
import com.foggyframework.dataset.model.PagingResult;
import com.foggyframework.dataset.model.PagingResultImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Configuration
public class FoggyDatasetClientConfiguration {

    @Bean
    public ReturnConverterManagerImpl returnConverterManager() throws NoSuchMethodException {
        ReturnConverterManagerImpl manager = new ReturnConverterManagerImpl();

        manager.register(PagingResult.class, new PagingReturnConverter(PagingResultImpl.class));
        manager.register(PagingResultImpl.class, new PagingReturnConverter(PagingResultImpl.class));

        manager.register(List.class, new ListReturnConverter());
        manager.register(Map.class, new MapReturnConverter());

        manager.register(int.class, new SimpleObjectReturnConverter<>(ObjectTransFormatter.INTEGER_TRANSFORMATTERINSTANCE));
        manager.register(Integer.class, new SimpleObjectReturnConverter<>(ObjectTransFormatter.INTEGER_TRANSFORMATTERINSTANCE));
        manager.register(Long.class, new SimpleObjectReturnConverter<>(ObjectTransFormatter.LONG_TRANSFORMATTERINSTANCE));
        manager.register(long.class, new SimpleObjectReturnConverter<>(ObjectTransFormatter.LONG_TRANSFORMATTERINSTANCE));
        manager.register(Double.class, new SimpleObjectReturnConverter<>(ObjectTransFormatter.DOUBLE_TRANSFORMATTERINSTANCE));
        manager.register(double.class, new SimpleObjectReturnConverter<>(ObjectTransFormatter.DOUBLE_TRANSFORMATTERINSTANCE));
        manager.register(Float.class, new SimpleObjectReturnConverter<>(ObjectTransFormatter.FLOAT_TRANSFORMATTERINSTANCE));
        manager.register(float.class, new SimpleObjectReturnConverter<>(ObjectTransFormatter.FLOAT_TRANSFORMATTERINSTANCE));
        manager.register(Boolean.class, new SimpleObjectReturnConverter<>(ObjectTransFormatter.BOOLEAN_TRANSFORMATTERINSTANCE));
        manager.register(boolean.class, new SimpleObjectReturnConverter<>(ObjectTransFormatter.BOOLEAN_TRANSFORMATTERINSTANCE));

        manager.register(String.class, new SimpleObjectReturnConverter<>(ObjectTransFormatter.STRING_TRANSFORMATTERINSTANCE));
        manager.register(Date.class, new SimpleObjectReturnConverter<>(ObjectTransFormatter.DATE_TRANSFORMATTERINSTANCE));
        manager.register(BigDecimal.class, new SimpleObjectReturnConverter<>(ObjectTransFormatter.BIGDECIMAL_TRANSFORMATTERINSTANCE));

        manager.register(FoggyDatasetClientConfiguration.class.getDeclaredMethod("listString").getGenericReturnType(), new ListStringReturnConverter());
        manager.register(FoggyDatasetClientConfiguration.class.getDeclaredMethod("listInteger").getGenericReturnType(), new ListIntegerReturnConverter());
        manager.register(FoggyDatasetClientConfiguration.class.getDeclaredMethod("listLong").getGenericReturnType(), new ListLongReturnConverter());
        manager.register(FoggyDatasetClientConfiguration.class.getDeclaredMethod("listDouble").getGenericReturnType(), new ListDoubleReturnConverter());
        manager.register(FoggyDatasetClientConfiguration.class.getDeclaredMethod("lisDate").getGenericReturnType(), new ListDateReturnConverter());
        manager.register(FoggyDatasetClientConfiguration.class.getDeclaredMethod("lisBoolean").getGenericReturnType(), new ListBooleanReturnConverter());
        manager.register(FoggyDatasetClientConfiguration.class.getDeclaredMethod("lisBigDecimal").getGenericReturnType(), new ListBigDecimalReturnConverter());
        return manager;
    }

    private List<String> listString() {
        return null;
    }

    private List<Integer> listInteger() {
        return null;
    }

    private List<Long> listLong() {
        return null;
    }

    private List<Double> listDouble() {
        return null;
    }

    private List<Date> lisDate() {
        return null;
    }

    private List<Boolean> lisBoolean() {
        return null;
    }

    private List<BigDecimal> lisBigDecimal() {
        return null;
    }
}
