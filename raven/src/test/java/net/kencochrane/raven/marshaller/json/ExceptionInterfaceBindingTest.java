package net.kencochrane.raven.marshaller.json;

import mockit.Delegate;
import mockit.Injectable;
import mockit.NonStrictExpectations;
import net.kencochrane.raven.event.interfaces.ExceptionInterface;
import net.kencochrane.raven.event.interfaces.ImmutableThrowable;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ExceptionInterfaceBindingTest {
    private ExceptionInterfaceBinding interfaceBinding;
    @Injectable
    private ExceptionInterface mockExceptionInterface;

    @BeforeMethod
    public void setUp() throws Exception {
        interfaceBinding = new ExceptionInterfaceBinding();
    }

    @Test
    public void testSimpleException() throws Exception {
        final JsonComparator jsonComparator = new JsonComparator();
        final String message = "6e65f60d-9f22-495a-9556-7a61eeea2a14";
        final Throwable throwable = new IllegalStateException(message);
        new NonStrictExpectations() {{
            mockExceptionInterface.getThrowable();
            result = new Delegate<Void>() {
                public ImmutableThrowable getThrowable() {
                    return new ImmutableThrowable(throwable);
                }
            };
        }};

        interfaceBinding.writeInterface(jsonComparator.getGenerator(), mockExceptionInterface);

        jsonComparator.assertSameAsResource("/net/kencochrane/raven/marshaller/json/Exception1.json");
    }
}
