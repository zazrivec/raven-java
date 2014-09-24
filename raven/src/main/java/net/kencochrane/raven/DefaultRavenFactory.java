package net.kencochrane.raven;

import net.kencochrane.raven.connection.AsyncConnection;
import net.kencochrane.raven.connection.Connection;
import net.kencochrane.raven.connection.HttpConnection;
import net.kencochrane.raven.connection.UdpConnection;
import net.kencochrane.raven.dsn.Dsn;
import net.kencochrane.raven.event.helper.HttpEventBuilderHelper;
import net.kencochrane.raven.event.interfaces.ExceptionInterface;
import net.kencochrane.raven.event.interfaces.HttpInterface;
import net.kencochrane.raven.event.interfaces.MessageInterface;
import net.kencochrane.raven.event.interfaces.StackTraceInterface;
import net.kencochrane.raven.marshaller.Marshaller;
import net.kencochrane.raven.marshaller.json.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default implementation of {@link RavenFactory}.
 * <p>
 * In most cases this is the implementation to use or extend for additional features.
 */
public class DefaultRavenFactory extends RavenFactory {
    //TODO: Add support for tags set by default
    /**
     * Protocol setting to disable security checks over an SSL connection.
     */
    public static final String NAIVE_PROTOCOL = "naive";
    /**
     * Option specific to raven-java, allowing to enable/disable the compression of requests to the Sentry Server.
     */
    public static final String COMPRESSION_OPTION = "raven.compression";
    /**
     * Option specific to raven-java, allowing to set a timeout (in ms) for a request to the Sentry server.
     */
    public static final String TIMEOUT_OPTION = "raven.timeout";
    /**
     * Option to send events asynchronously.
     */
    public static final String ASYNC_OPTION = "raven.async";
    /**
     * Option to disable the graceful shutdown.
     */
    public static final String GRACEFUL_SHUTDOWN_OPTION = "raven.async.gracefulshutdown";
    /**
     * Option for the number of threads assigned for the connection.
     */
    public static final String MAX_THREADS_OPTION = "raven.async.threads";
    /**
     * Option for the priority of threads assigned for the connection.
     */
    public static final String PRIORITY_OPTION = "raven.async.priority";
    /**
     * Option for the maximum size of the queue.
     */
    public static final String QUEUE_SIZE_OPTION = "raven.async.queuesize";
    /**
     * Option to hide common stackframes with enclosing exceptions.
     */
    public static final String HIDE_COMMON_FRAMES_OPTION = "raven.stacktrace.hidecommon";
    private static final Logger logger = LoggerFactory.getLogger(DefaultRavenFactory.class);
    private static final String FALSE = Boolean.FALSE.toString();

    @Override
    public Raven createRavenInstance(Dsn dsn) {
        Raven raven = new Raven();
        raven.setConnection(createConnection(dsn));
        try {
            Class.forName("javax.servlet.Servlet", false, this.getClass().getClassLoader());
            raven.addBuilderHelper(new HttpEventBuilderHelper());
        } catch (ClassNotFoundException e) {
            logger.debug("It seems that the current environment doesn't provide access to servlets.");
        }
        return raven;
    }

    /**
     * Creates a connection to the given DSN by determining the protocol.
     *
     * @param dsn Data Source Name of the Sentry server to use.
     * @return a connection to the server.
     */
    protected Connection createConnection(Dsn dsn) {
        String protocol = dsn.getProtocol();
        Connection connection;

        if (protocol.equalsIgnoreCase("http") || protocol.equalsIgnoreCase("https")) {
            logger.info("Using an HTTP connection to Sentry.");
            connection = createHttpConnection(dsn);
        } else if (protocol.equalsIgnoreCase("udp")) {
            logger.info("Using an UDP connection to Sentry.");
            connection = createUdpConnection(dsn);
        } else {
            throw new IllegalStateException("Couldn't create a connection for the protocol '" + protocol + "'");
        }

        // Enable async unless its value is 'false'.
        if (!FALSE.equalsIgnoreCase(dsn.getOptions().get(ASYNC_OPTION))) {
            connection = createAsyncConnection(dsn, connection);
        }

        return connection;
    }

    /**
     * Encapsulates an already existing connection in an {@link AsyncConnection} and get the async options from the
     * Sentry DSN.
     *
     * @param dsn        Data Source Name of the Sentry server.
     * @param connection Connection to encapsulate in an {@link AsyncConnection}.
     * @return the asynchronous connection.
     */
    protected Connection createAsyncConnection(Dsn dsn, Connection connection) {

        int maxThreads;
        if (dsn.getOptions().containsKey(MAX_THREADS_OPTION)) {
            maxThreads = Integer.parseInt(dsn.getOptions().get(MAX_THREADS_OPTION));
        } else {
            maxThreads = Runtime.getRuntime().availableProcessors();
        }

        int priority;
        if (dsn.getOptions().containsKey(PRIORITY_OPTION)) {
            priority = Integer.parseInt(dsn.getOptions().get(PRIORITY_OPTION));
        } else {
            priority = Thread.MIN_PRIORITY;
        }

        BlockingDeque<Runnable> queue;
        if (dsn.getOptions().containsKey(QUEUE_SIZE_OPTION)) {
            queue = new LinkedBlockingDeque<Runnable>(Integer.parseInt(dsn.getOptions().get(QUEUE_SIZE_OPTION)));
        } else {
            queue = new LinkedBlockingDeque<Runnable>();
        }

        ExecutorService executorService = new ThreadPoolExecutor(
                maxThreads, maxThreads, 0L, TimeUnit.MILLISECONDS, queue,
                new DaemonThreadFactory(priority), new ThreadPoolExecutor.DiscardOldestPolicy());

        boolean gracefulShutdown = !FALSE.equalsIgnoreCase(dsn.getOptions().get(GRACEFUL_SHUTDOWN_OPTION));

        return new AsyncConnection(connection, executorService, gracefulShutdown);
    }

    /**
     * Creates an HTTP connection to the Sentry server.
     *
     * @param dsn Data Source Name of the Sentry server.
     * @return an {@link HttpConnection} to the server.
     */
    protected Connection createHttpConnection(Dsn dsn) {
        URL sentryApiUrl = HttpConnection.getSentryApiUrl(dsn.getUri(), dsn.getProjectId());
        HttpConnection httpConnection = new HttpConnection(sentryApiUrl, dsn.getPublicKey(), dsn.getSecretKey());
        httpConnection.setMarshaller(createMarshaller(dsn));

        // Set the naive mode
        httpConnection.setBypassSecurity(dsn.getProtocolSettings().contains(NAIVE_PROTOCOL));
        // Set the HTTP timeout
        if (dsn.getOptions().containsKey(TIMEOUT_OPTION))
            httpConnection.setTimeout(Integer.parseInt(dsn.getOptions().get(TIMEOUT_OPTION)));
        return httpConnection;
    }

    /**
     * Creates an UDP connection to the Sentry server.
     *
     * @param dsn Data Source Name of the Sentry server.
     * @return an {@link UdpConnection} to the server.
     */
    protected Connection createUdpConnection(Dsn dsn) {
        int port = dsn.getPort() != -1 ? dsn.getPort() : UdpConnection.DEFAULT_UDP_PORT;
        UdpConnection udpConnection = new UdpConnection(dsn.getHost(), port, dsn.getPublicKey(), dsn.getSecretKey());
        udpConnection.setMarshaller(createMarshaller(dsn));
        return udpConnection;
    }

    /**
     * Creates a JSON marshaller that will convert every {@link net.kencochrane.raven.event.Event} in a format
     * handled by the Sentry server.
     *
     * @param dsn Data Source Name of the Sentry server.
     * @return a {@link JsonMarshaller} to process the events.
     */
    protected Marshaller createMarshaller(Dsn dsn) {
        JsonMarshaller marshaller = new JsonMarshaller();

        // Set JSON marshaller bindings
        StackTraceInterfaceBinding stackTraceBinding = new StackTraceInterfaceBinding();
        // Enable common frames hiding unless its value is 'false'.
        stackTraceBinding.setRemoveCommonFramesWithEnclosing(
                !FALSE.equalsIgnoreCase(dsn.getOptions().get(HIDE_COMMON_FRAMES_OPTION)));
        stackTraceBinding.setNotInAppFrames(getNotInAppFrames());

        marshaller.addInterfaceBinding(StackTraceInterface.class, stackTraceBinding);
        marshaller.addInterfaceBinding(ExceptionInterface.class, new ExceptionInterfaceBinding(stackTraceBinding));
        marshaller.addInterfaceBinding(MessageInterface.class, new MessageInterfaceBinding());
        HttpInterfaceBinding httpBinding = new HttpInterfaceBinding();
        //TODO: Add a way to clean the HttpRequest
        //httpBinding.
        marshaller.addInterfaceBinding(HttpInterface.class, httpBinding);

        // Enable compression unless the option is set to false
        marshaller.setCompression(!FALSE.equalsIgnoreCase(dsn.getOptions().get(COMPRESSION_OPTION)));

        return marshaller;
    }

    /**
     * Provides a list of package names to consider as "not in-app".
     * <p>
     * Those packages will be used with the {@link StackTraceInterface} to hide frames that aren't a part of
     * the main application.
     *
     * @return the list of "not in-app" packages.
     */
    protected Collection<String> getNotInAppFrames() {
        return Arrays.asList("com.sun.",
                "java.",
                "javax.",
                "org.omg.",
                "sun.",
                "junit.",
                "com.ibm.",
                "com.intellij.rt.");
    }

    /**
     * Thread factory generating daemon threads with a custom priority.
     * <p>
     * Those (usually) low priority threads will allow to send event details to sentry concurrently without slowing
     * down the main application.
     */
    @SuppressWarnings("PMD.AvoidThreadGroup")
    protected static final class DaemonThreadFactory implements ThreadFactory {
        private static final AtomicInteger POOL_NUMBER = new AtomicInteger(1);
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;
        private final int priority;

        private DaemonThreadFactory(int priority) {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
            namePrefix = "raven-pool-" + POOL_NUMBER.getAndIncrement() + "-thread-";
            this.priority = priority;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
            if (!t.isDaemon())
                t.setDaemon(true);
            if (t.getPriority() != priority)
                t.setPriority(priority);
            return t;
        }
    }
}
