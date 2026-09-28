package org.aliveclean;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Retain only the last failed operation, including reflective/native causes. */
final class XiaomiFailure {
    static String describe(Context context, String operation, Throwable error) {
        try (PrintWriter out = new PrintWriter(new FileOutputStream(
                new File(context.getCacheDir(), "xiaomi-last-failure.txt")))) {
            out.println(operation);
            error.printStackTrace(out);
        } catch (Exception ignored) {}
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        Throwable cause = error;
        while (cause.getCause() != null && visited.add(cause.getCause())) cause = cause.getCause();
        return cause.toString();
    }
}
