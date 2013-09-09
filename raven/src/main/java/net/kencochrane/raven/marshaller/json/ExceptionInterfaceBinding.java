package net.kencochrane.raven.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import net.kencochrane.raven.event.interfaces.ExceptionInterface;
import net.kencochrane.raven.event.interfaces.ImmutableThrowable;

import java.io.IOException;

/**
 * Binding system allowing to convert an {@link ExceptionInterface} to a JSON stream.
 */
public class ExceptionInterfaceBinding implements InterfaceBinding<ExceptionInterface> {
    private static final String TYPE_PARAMETER = "type";
    private static final String VALUE_PARAMETER = "value";
    private static final String MODULE_PARAMETER = "module";
    private static final String DEFAULT_PACKAGE_NAME = "(default)";

    @Override
    public void writeInterface(JsonGenerator generator, ExceptionInterface exceptionInterface) throws IOException {
        ImmutableThrowable throwable = exceptionInterface.getThrowable();

        generator.writeStartObject();
        generator.writeStringField(TYPE_PARAMETER, throwable.getActualClass().getSimpleName());
        generator.writeStringField(VALUE_PARAMETER, throwable.getMessage());
        Package aPackage = throwable.getActualClass().getPackage();
        generator.writeStringField(MODULE_PARAMETER, (aPackage != null) ? aPackage.getName() : DEFAULT_PACKAGE_NAME);
        generator.writeEndObject();
    }
}
