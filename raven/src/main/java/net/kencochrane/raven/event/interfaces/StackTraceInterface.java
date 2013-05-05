package net.kencochrane.raven.event.interfaces;

/**
 * The StackTrace interface for Sentry, allowing to add a stackTrace to an event.
 */
public class StackTraceInterface implements SentryInterface {
    /**
     * Name of the Sentry interface allowing to send a StackTrace.
     */
    public static final String STACKTRACE_INTERFACE = "sentry.interfaces.Stacktrace";
    private final ImmutableThrowable throwable;

    public StackTraceInterface(Throwable throwable) {
        this.throwable = new ImmutableThrowable(throwable);
    }

    @Override
    public String getInterfaceName() {
        return STACKTRACE_INTERFACE;
    }

    public ImmutableThrowable getThrowable() {
        return throwable;
    }
}
