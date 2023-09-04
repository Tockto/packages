package io.flutter.plugins.camera.ext;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.flutter.plugin.common.MethodChannel;
//import io.sentry.Attachment;
//import io.sentry.Hint;
//import io.sentry.Sentry;

public class MediaExtension {

    private static final String TAG = "MediaExtension";
    private final List<String> timestamp = new ArrayList<>();

    public void addTimestamp() {
        Long ts = System.currentTimeMillis();
        timestamp.add(ts.toString());
    }

    private void writeTimestampToFile(File output) throws IOException {
        Log.d(TAG, "Writing timestamp file to " + output.getAbsolutePath());
        BufferedWriter writer = new BufferedWriter(new FileWriter(output));
        writer.append(String.join("\n", timestamp));
        writer.close();
    }

    private void writeAudioToFile(File source, File output) throws IOException {
        Log.d(TAG, "Writing audio file to " + output.getAbsolutePath());
        Helper.extractAudio(source.getAbsolutePath(), output.getAbsolutePath());
    }

    public void saveExtensionFiles(@NonNull final MethodChannel.Result result, @NonNull final File captureFile) {
        // Create output files
        String fileName = captureFile.getName().replaceFirst("[.][^.]+$", "");
        File audioFile = new File(captureFile.getParentFile(), fileName + ".m4a");
        File timestampFile = new File(captureFile.getParentFile(), fileName + ".txt");

        try {
            writeAudioToFile(captureFile, audioFile);
            writeTimestampToFile(timestampFile);
        } catch (Exception e1) {

            Log.e(TAG, "Error saving extension files", e1);

            // Capture log
            //Sentry.captureException(e1, Hint.withAttachment(new Attachment(captureFile.getAbsolutePath())));

            // Safe clean up
            try {
                boolean isErrorDeleteAudio =  audioFile.exists() && !audioFile.delete();
                boolean isErrorDeleteTimestamp = timestampFile.exists() && !timestampFile.delete();

                // Always remove file if failed
                if(isErrorDeleteAudio || isErrorDeleteTimestamp) {
                    result.error("videoRecordingFailed", "Cannot remove m4a/txt file", null);
                    return;
                }

            } catch (Exception e2) {
                result.error("videoRecordingFailed", e2.getMessage(), null);
                return;
            }
        }
    }

}
