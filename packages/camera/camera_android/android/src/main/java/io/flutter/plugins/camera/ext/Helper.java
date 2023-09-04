package io.flutter.plugins.camera.ext;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;


public class Helper {

    private static final int DEFAULT_BUFFER_SIZE = 1024 * 1024;
    private static final String TAG = "AudioExtractor";

    @SuppressLint("WrongConstant")
    public static void extractAudio(@NonNull final String srcPath, @NonNull final String dstPath) throws IOException {
        // Set up MediaExtractor to read from the source.
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(srcPath);

        // Set up MediaMuxer for the destination.
        MediaMuxer muxer = new MediaMuxer(dstPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        // Set up the tracks and retrieve the max buffer size for selected tracks
        int trackCount = extractor.getTrackCount();
        HashMap<Integer, Integer> indexMap = new HashMap<>(trackCount);
        int bufferSize = -1;
        for (int i = 0; i < trackCount; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            Log.d(TAG, String.format("Found %s at track %d", mime, i));
            boolean selectCurrentTrack = mime.startsWith("audio/");
            if (selectCurrentTrack) {
                extractor.selectTrack(i);
                int dstIndex = muxer.addTrack(format);
                indexMap.put(i, dstIndex);
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    int newSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                    bufferSize = Math.max(newSize, bufferSize);
                }
            }
        }
        if (bufferSize < 0) {
            bufferSize = DEFAULT_BUFFER_SIZE;
        }
        if (indexMap.isEmpty()) {
            Log.d(TAG, "Cannot find any audio track from sample! Need at least one track.");
            throw new IllegalStateException("Cannot find audio track!");
        }

        // Copy the samples from MediaExtractor to MediaMuxer. We will loop
        // for copying each sample and stop when we get to the end of the source
        // file or exceed the end time of the trimming.
        int offset = 0;
        int trackIndex = -1;
        ByteBuffer dstBuf = ByteBuffer.allocate(bufferSize);
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        muxer.start();
        while (true) {
            bufferInfo.offset = offset;
            bufferInfo.size = extractor.readSampleData(dstBuf, offset);
            if (bufferInfo.size < 0) {
                Log.d(TAG, "Found input EOS");
                bufferInfo.size = 0;
                break;
            } else {
                bufferInfo.flags = extractor.getSampleFlags();
                trackIndex = extractor.getSampleTrackIndex();
                muxer.writeSampleData(indexMap.get(trackIndex), dstBuf, bufferInfo);
                extractor.advance();
            }
        }

        // Release resource
        muxer.stop();
        muxer.release();
        extractor.release();
    }
}

