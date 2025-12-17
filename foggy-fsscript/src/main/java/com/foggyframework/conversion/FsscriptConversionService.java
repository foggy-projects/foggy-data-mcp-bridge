package com.foggyframework.conversion;

import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConverterRegistry;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.lang.Nullable;

public class FsscriptConversionService extends GenericConversionService {
    @Nullable
    private static volatile FsscriptConversionService sharedInstance;


    /**
     * Create a new {@code DefaultConversionService} with the set of
     * {@linkplain DefaultConversionService#addDefaultConverters(ConverterRegistry) default converters}.
     */
    public FsscriptConversionService() {
        addDefaultConverters(this);
    }


    /**
     * Return a shared default {@code ConversionService} instance,
     * lazily building it once needed.
     * <p><b>NOTE:</b> We highly recommend constructing individual
     * {@code ConversionService} instances for customization purposes.
     * This accessor is only meant as a fallback for code paths which
     * need simple type coercion but cannot access a longer-lived
     * {@code ConversionService} instance any other way.
     * @return the shared {@code ConversionService} instance (never {@code null})
     * @since 4.3.5
     */
    public static ConversionService getSharedInstance() {
        FsscriptConversionService cs = sharedInstance;
        if (cs == null) {
            synchronized (FsscriptConversionService.class) {
                cs = sharedInstance;
                if (cs == null) {
                    cs = new FsscriptConversionService();
                    sharedInstance = cs;
                }
            }
        }
        return cs;
    }

    /**
     * Add converters appropriate for most environments.
     * @param converterRegistry the registry of converters to add to
     * (must also be castable to ConversionService, e.g. being a {@link ConfigurableConversionService})
     * @throws ClassCastException if the given ConverterRegistry could not be cast to a ConversionService
     */
    public  void addDefaultConverters(ConverterRegistry converterRegistry) {
        addScalarConverters(converterRegistry);
        addCollectionConverters(converterRegistry);
        DefaultConversionService.addDefaultConverters(converterRegistry);
//        converterRegistry.addConverter(new ByteBufferConverter((ConversionService) converterRegistry));
//        converterRegistry.addConverter(new StringToTimeZoneConverter());
//        converterRegistry.addConverter(new ZoneIdToTimeZoneConverter());
//        converterRegistry.addConverter(new ZonedDateTimeToCalendarConverter());
//
//        converterRegistry.addConverter(new ObjectToObjectConverter());
//        converterRegistry.addConverter(new IdToEntityConverter((ConversionService) converterRegistry));
//        converterRegistry.addConverter(new FallbackObjectToStringConverter());
//        converterRegistry.addConverter(new ObjectToOptionalConverter((ConversionService) converterRegistry));
    }
private MapToObjectConverter mapToObjectConverter = new MapToObjectConverter(this);
    /**
     * Add common collection converters.
     * @param converterRegistry the registry of converters to add to
     * (must also be castable to ConversionService, e.g. being a {@link ConfigurableConversionService})
     * @throws ClassCastException if the given ConverterRegistry could not be cast to a ConversionService
     * @since 4.2.3
     */
    public  void addCollectionConverters(ConverterRegistry converterRegistry) {
        ConversionService conversionService = (ConversionService) converterRegistry;

        converterRegistry.addConverter(mapToObjectConverter);
        DefaultConversionService.addCollectionConverters(converterRegistry);
//        converterRegistry.addConverter(new ArrayToCollectionConverter(conversionService));
//        converterRegistry.addConverter(new CollectionToArrayConverter(conversionService));
//
//        converterRegistry.addConverter(new ArrayToArrayConverter(conversionService));
//        converterRegistry.addConverter(new CollectionToCollectionConverter(conversionService));
//        converterRegistry.addConverter(new MapToMapConverter(conversionService));
//
//        converterRegistry.addConverter(new ArrayToStringConverter(conversionService));
//        converterRegistry.addConverter(new StringToArrayConverter(conversionService));
//
//        converterRegistry.addConverter(new ArrayToObjectConverter(conversionService));
//        converterRegistry.addConverter(new ObjectToArrayConverter(conversionService));
//
//        converterRegistry.addConverter(new CollectionToStringConverter(conversionService));
//        converterRegistry.addConverter(new StringToCollectionConverter(conversionService));
//
//        converterRegistry.addConverter(new CollectionToObjectConverter(conversionService));
//        converterRegistry.addConverter(new ObjectToCollectionConverter(conversionService));
//
//        converterRegistry.addConverter(new StreamConverter(conversionService));
    }

    @Override
    protected GenericConverter getDefaultConverter(TypeDescriptor sourceType, TypeDescriptor targetType) {
        if(sourceType.isMap()){
//            this.get
            return  mapToObjectConverter;
        }
        return super.getDefaultConverter(sourceType, targetType);
    }

    private  void addScalarConverters(ConverterRegistry converterRegistry) {
//        converterRegistry.addConverterFactory(new NumberToNumberConverterFactory());
//
//        converterRegistry.addConverterFactory(new StringToNumberConverterFactory());
//        converterRegistry.addConverter(Number.class, String.class, new ObjectToStringConverter());
//

//        converterRegistry.addConverter(Character.class, String.class, new ObjectToStringConverter());
//
//        converterRegistry.addConverter(new NumberToCharacterConverter());
//        converterRegistry.addConverterFactory(new CharacterToNumberFactory());
//
//        converterRegistry.addConverter(new StringToBooleanConverter());
//        converterRegistry.addConverter(Boolean.class, String.class, new ObjectToStringConverter());
//
//        converterRegistry.addConverterFactory(new StringToEnumConverterFactory());
//        converterRegistry.addConverter(new EnumToStringConverter((ConversionService) converterRegistry));
//
//        converterRegistry.addConverterFactory(new IntegerToEnumConverterFactory());
//        converterRegistry.addConverter(new EnumToIntegerConverter((ConversionService) converterRegistry));
//
//        converterRegistry.addConverter(new StringToLocaleConverter());
//        converterRegistry.addConverter(Locale.class, String.class, new ObjectToStringConverter());
//
//        converterRegistry.addConverter(new StringToCharsetConverter());
//        converterRegistry.addConverter(Charset.class, String.class, new ObjectToStringConverter());
//
//        converterRegistry.addConverter(new StringToCurrencyConverter());
//        converterRegistry.addConverter(Currency.class, String.class, new ObjectToStringConverter());
//
//        converterRegistry.addConverter(new StringToPropertiesConverter());
//        converterRegistry.addConverter(new PropertiesToStringConverter());
//
//        converterRegistry.addConverter(new StringToUUIDConverter());
//        converterRegistry.addConverter(UUID.class, String.class, new ObjectToStringConverter());
    }
}
