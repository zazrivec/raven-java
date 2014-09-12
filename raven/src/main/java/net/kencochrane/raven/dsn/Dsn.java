package net.kencochrane.raven.dsn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.*;

/**
 * Data Source name allowing a direct connection to a Sentry server.
 */
public class Dsn {
    /**
     * Name of the environment and system variables containing the DSN.
     */
    public static final String DSN_VARIABLE = "SENTRY_DSN";
    private static final Logger logger = LoggerFactory.getLogger(Dsn.class);
    private String secretKey;
    private String publicKey;
    private String projectId;
    private String protocol;
    private String host;
    private int port;
    private String path;
    private Set<String> protocolSettings;
    private Map<String, String> options;
    private URI uri;

    /**
     * Creates a DSN based on the {@link #dsnLookup()} result.
     */
    public Dsn() {
        this(dsnLookup());
    }

    /**
     * Creates a DSN based on a String.
     *
     * @param dsn DSN in a string form.
     * @throws InvalidDsnException the given DSN is not valid.
     */
    public Dsn(String dsn) throws InvalidDsnException {
        this(URI.create(dsn));
    }

    /**
     * Creates a DSN based on a URI.
     *
     * @param dsn DSN in URI form.
     * @throws InvalidDsnException the given DSN is not valid.
     */
    public Dsn(URI dsn) throws InvalidDsnException {
        if (dsn == null)
            throw new InvalidDsnException("The sentry DSN must be provided and not be null");

        options = new HashMap<String, String>();
        protocolSettings = new HashSet<String>();

        extractProtocolInfo(dsn);
        extractUserKeys(dsn);
        extractHostInfo(dsn);
        extractPathInfo(dsn);
        extractOptions(dsn);

        makeOptionsImmutable();

        validate();

        try {
            uri = new URI(protocol, null, host, port, path, null, null);
        } catch (URISyntaxException e) {
            throw new InvalidDsnException("Impossible to determine Sentry's URI from the DSN '" + dsn + "'", e);
        }
    }

    /**
     * Looks for a DSN configuration within JNDI, the System environment or Java properties.
     *
     * @return a DSN configuration or null if nothing could be found.
     */
    public static String dsnLookup() {
        String dsn = null;

        // Try to obtain the DSN from JNDI
        try {
            // Check that JNDI is available (not available on Android) by loading InitialContext
            Class.forName("javax.naming.InitialContext", false, Dsn.class.getClassLoader());
            dsn = JndiLookup.jndiLookup();
        } catch (ClassNotFoundException e) {
            logger.debug("JNDI not available");
        } catch (NoClassDefFoundError e) {
            logger.debug("JNDI not available");
        }

        // Try to obtain the DSN from a System Environment Variable
        if (dsn == null)
            dsn = System.getenv(DSN_VARIABLE);

        // Try to obtain the DSN from a Java System Property
        if (dsn == null)
            dsn = System.getProperty(DSN_VARIABLE);

        return dsn;
    }

    /**
     * Extracts the path and the project ID from the DSN provided as an {@code URI}.
     *
     * @param dsnUri DSN as an URI.
     */
    private void extractPathInfo(URI dsnUri) {
        String uriPath = dsnUri.getPath();
        if (uriPath == null)
            return;
        int projectIdStart = uriPath.lastIndexOf("/") + 1;
        path = uriPath.substring(0, projectIdStart);
        projectId = uriPath.substring(projectIdStart);
    }

    /**
     * Extracts the hostname and port of the Sentry server from the DSN provided as an {@code URI}.
     *
     * @param dsnUri DSN as an URI.
     */
    private void extractHostInfo(URI dsnUri) {
        host = dsnUri.getHost();
        port = dsnUri.getPort();
    }

    /**
     * Extracts the scheme and additional protocol options from the DSN provided as an {@code URI}.
     *
     * @param dsnUri DSN as an URI.
     */
    private void extractProtocolInfo(URI dsnUri) {
        String scheme = dsnUri.getScheme();
        if (scheme == null)
            return;
        String[] schemeDetails = scheme.split("\\+");
        protocolSettings.addAll(Arrays.asList(schemeDetails).subList(0, schemeDetails.length - 1));
        protocol = schemeDetails[schemeDetails.length - 1];
    }

    /**
     * Extracts the public and secret keys from the DSN provided as an {@code URI}.
     *
     * @param dsnUri DSN as an URI.
     */
    private void extractUserKeys(URI dsnUri) {
        String userInfo = dsnUri.getUserInfo();
        if (userInfo == null)
            return;
        String[] userDetails = userInfo.split(":");
        publicKey = userDetails[0];
        if (userDetails.length > 1)
            secretKey = userDetails[1];
    }

    /**
     * Extracts the DSN options from the DSN provided as an {@code URI}.
     *
     * @param dsnUri DSN as an URI.
     */
    private void extractOptions(URI dsnUri) {
        String query = dsnUri.getQuery();
        if (query == null || query.isEmpty())
            return;
        for (String optionPair : query.split("&")) {
            try {
                String[] pairDetails = optionPair.split("=");
                String key = URLDecoder.decode(pairDetails[0], "UTF-8");
                String value = pairDetails.length > 1 ? URLDecoder.decode(pairDetails[1], "UTF-8") : null;
                options.put(key, value);
            } catch (UnsupportedEncodingException e) {
                throw new IllegalArgumentException("Impossible to decode the query parameter '" + optionPair + "'", e);
            }
        }
    }

    /**
     * Makes protocol and dsn options immutable to allow external usage.
     */
    private void makeOptionsImmutable() {
        // Make the options immutable
        options = Collections.unmodifiableMap(options);
        protocolSettings = Collections.unmodifiableSet(protocolSettings);
    }

    /**
     * Validates internally the DSN, and check for mandatory elements.
     * <p>
     * Mandatory elements are the {@link #host}, {@link #publicKey}, {@link #secretKey} and {@link #projectId}.
     */
    private void validate() {
        List<String> missingElements = new LinkedList<String>();
        if (host == null)
            missingElements.add("host");
        if (publicKey == null)
            missingElements.add("public key");
        if (secretKey == null)
            missingElements.add("secret key");
        if (projectId == null || projectId.isEmpty())
            missingElements.add("project ID");

        if (!missingElements.isEmpty())
            throw new InvalidDsnException("Invalid DSN, the following properties aren't set '" + missingElements + "'");
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getPath() {
        return path;
    }

    public Set<String> getProtocolSettings() {
        return protocolSettings;
    }

    public Map<String, String> getOptions() {
        return options;
    }

    /**
     * Creates the URI of the sentry server.
     *
     * @return the URI of the sentry server.
     */
    public URI getUri() {
        return uri;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Dsn dsn = (Dsn) o;

        if (port != dsn.port) return false;
        if (!host.equals(dsn.host)) return false;
        if (!options.equals(dsn.options)) return false;
        if (!path.equals(dsn.path)) return false;
        if (!projectId.equals(dsn.projectId)) return false;
        if (protocol != null ? !protocol.equals(dsn.protocol) : dsn.protocol != null) return false;
        if (!protocolSettings.equals(dsn.protocolSettings)) return false;
        if (!publicKey.equals(dsn.publicKey)) return false;
        if (!secretKey.equals(dsn.secretKey)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = publicKey.hashCode();
        result = 31 * result + projectId.hashCode();
        result = 31 * result + host.hashCode();
        result = 31 * result + port;
        result = 31 * result + path.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Dsn{"
                + "uri=" + uri
                + '}';
    }
}
