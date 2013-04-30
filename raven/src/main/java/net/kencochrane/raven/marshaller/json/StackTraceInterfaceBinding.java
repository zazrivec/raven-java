package net.kencochrane.raven.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import net.kencochrane.raven.event.interfaces.ImmutableThrowable;
import net.kencochrane.raven.event.interfaces.StackTraceInterface;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public class StackTraceInterfaceBinding implements InterfaceBinding<StackTraceInterface> {
    private static final Logger logger = Logger.getLogger(StackTraceInterfaceBinding.class.getCanonicalName());
    private static final String FRAMES_PARAMETER = "frames";
    private static final String FILENAME_PARAMETER = "filename";
    private static final String FUNCTION_PARAMETER = "function";
    private static final String MODULE_PARAMETER = "module";
    private static final String LINE_NO_PARAMETER = "lineno";
    private static final String ABSOLUTE_PATH_PARAMETER = "abs_path";
    private static final String CONTEXT_LINE_PARAMETER = "context_line";
    private static final String PRE_CONTEXT_PARAMETER = "pre_context";
    private static final String POST_CONTEXT_PARAMETER = "post_context";
    private static final String IN_APP_PARAMETER = "in_app";
    private static final String VARIABLES_PARAMETER = "vars";
    private Set<String> notInAppFrames = Collections.emptySet();
    private boolean removeCommonFramesWithEnclosing = true;

    /**
     * Writes a fake frame to allow chained exceptions.
     *
     * @param throwable Exception for which a fake frame should be created
     */
    private void writeFakeFrame(JsonGenerator generator, ImmutableThrowable throwable) throws IOException {
        String message = "Caused by: " + throwable.getActualClass().getName();
        if (throwable.getMessage() != null)
            message += " (\"" + throwable.getMessage() + "\")";

        generator.writeStartObject();
        generator.writeStringField(MODULE_PARAMETER, message);
        generator.writeBooleanField(IN_APP_PARAMETER, true);
        generator.writeEndObject();
    }

    /**
     * Writes a single frame based on a {@code StackTraceElement}.
     *
     * @param stackTraceElement current frame in the stackTrace.
     */
    private void writeFrame(JsonGenerator generator, StackTraceElement stackTraceElement, boolean commonWithEnclosing)
            throws IOException {
        generator.writeStartObject();
        // Do not display the file name (irrelevant) as it replaces the module in the sentry interface.
        //generator.writeStringField(FILENAME_PARAMETER, stackTraceElement.getFileName());
        generator.writeStringField(MODULE_PARAMETER, stackTraceElement.getClassName());
        generator.writeBooleanField(IN_APP_PARAMETER, !(removeCommonFramesWithEnclosing && commonWithEnclosing)
                && isFrameInApp(stackTraceElement));
        generator.writeStringField(FUNCTION_PARAMETER, stackTraceElement.getMethodName());
        generator.writeNumberField(LINE_NO_PARAMETER, stackTraceElement.getLineNumber());
        generator.writeEndObject();
    }

    private boolean isFrameInApp(StackTraceElement stackTraceElement) {
        //TODO: A set is absolutely not efficient here, a Trie could be a better solution.
        for (String notInAppFrame : notInAppFrames) {
            if (stackTraceElement.getClassName().startsWith(notInAppFrame)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void writeInterface(JsonGenerator generator, StackTraceInterface stackTraceInterface) throws IOException {
        Deque<ImmutableThrowable> throwableStack = reverseExceptionChain(stackTraceInterface.getThrowable());

        generator.writeStartObject();
        generator.writeArrayFieldStart(FRAMES_PARAMETER);

        StackTraceElement[] enclosingStackTrace = new StackTraceElement[0];
        while (!throwableStack.isEmpty()) {
            ImmutableThrowable currentThrowable = throwableStack.pop();
            StackTraceElement[] stackTrace = currentThrowable.getStackTrace();

            // commonFrame is a switch that can't go back to true
            boolean commonFrame = true;
            // Go through the stackTrace frames from the first call to the last
            for (int i = stackTrace.length - 1, j = enclosingStackTrace.length - 1; i >= 0; i--, j--) {
                // Frames are always in common until one frame differs
                commonFrame = commonFrame && (j >= 0 && stackTrace[i].equals(enclosingStackTrace[j]));
                writeFrame(generator, stackTrace[i], commonFrame);
            }

            // Exceptions can't be chained, add a fake frame containing "Caused by ....:"
            if (!throwableStack.isEmpty())
                writeFakeFrame(generator, currentThrowable);

            enclosingStackTrace = stackTrace;
        }
        generator.writeEndArray();
        generator.writeEndObject();
    }

    private Deque<ImmutableThrowable> reverseExceptionChain(ImmutableThrowable originalThrowable) {
        Set<ImmutableThrowable> dejaVu = new HashSet<ImmutableThrowable>();
        Deque<ImmutableThrowable> throwableStack = new LinkedList<ImmutableThrowable>();

        ImmutableThrowable currentThrowable = originalThrowable;

        //Inverse the chain of exceptions to get the first exception thrown first.
        while (currentThrowable != null) {
            dejaVu.add(currentThrowable);
            throwableStack.push(currentThrowable);
            currentThrowable = currentThrowable.getCause();
            if (dejaVu.contains(currentThrowable)) {
                logger.warning("Exiting a circular referencing exception!");
                break;
            }
        }

        return throwableStack;
    }

    public void setRemoveCommonFramesWithEnclosing(boolean removeCommonFramesWithEnclosing) {
        this.removeCommonFramesWithEnclosing = removeCommonFramesWithEnclosing;
    }

    public void setNotInAppFrames(Set<String> notInAppFrames) {
        this.notInAppFrames = notInAppFrames;
    }
}
