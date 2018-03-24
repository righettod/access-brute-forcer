package righettod.eu.accessbf;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.support.annotation.Nullable;
import android.util.Log;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Service in charge of managing the file downloaded through the Android Download Manager.
 * In our case it's a password dictionary.
 */
public class PostDownloadService extends IntentService {
    public static final String ACTION_DL_FILE_PROCESS = "righettod.eu.smbaccessbf.action.dl.file.process";
    public static final String EXTRA_PARAM_DL_FILE_PATH = "righettod.eu.smbaccessbf.extra.dl.filepath";
    public static final String EXTRA_PARAM_TARGET_FILE_PATH = "righettod.eu.smbaccessbf.extra.target.filepath";

    /**
     * Constructor
     */
    public PostDownloadService() {
        super("PostDownloadService");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        ParcelFileDescriptor descriptor = null;
        try {
            if (intent != null && ACTION_DL_FILE_PROCESS.equals(intent.getAction())) {
                String filePath = intent.getStringExtra(EXTRA_PARAM_DL_FILE_PATH);
                File targetFilePath = new File(intent.getStringExtra(EXTRA_PARAM_TARGET_FILE_PATH));
                ContentResolver resolver = getContentResolver();
                descriptor = resolver.openFileDescriptor(Uri.parse(filePath), "r");
                //Extract archive or move file if it is not a supported archive
                if (filePath.toLowerCase().endsWith(".bz2")) {
                    NotificationUtil.sendNotification(getApplicationContext(), "Decompress dictionary...");
                    this.decompressBZip2Archive(descriptor.getFileDescriptor(), targetFilePath);
                } else {
                    NotificationUtil.sendNotification(getApplicationContext(), "Copy dictionary...");
                    this.copyFile(descriptor.getFileDescriptor(), targetFilePath);
                }
                NotificationUtil.sendNotification(getApplicationContext(), "Dictionary ready to be used !");
            }
        } catch (Exception e) {
            String msg = "Error during the file processing: " + e.getMessage();
            NotificationUtil.sendNotification(getApplicationContext(), msg);
            Log.e(MainActivity.LOG_TAG, msg, e);
        } finally {
            //Release descriptor
            if (descriptor != null) {
                try {
                    descriptor.close();
                } catch (IOException e) {
                    Log.e(MainActivity.LOG_TAG, "Cannot close the file descriptor !", e);
                }
            }


        }
    }


    /**
     * Decompress the BZ2 archive to a file
     *
     * @param inputArchive Pointer to the archive to uncompress
     * @param outputFile   Target file
     * @throws Exception If uncompress fail
     */

    private void decompressBZip2Archive(FileDescriptor inputArchive, File outputFile) throws Exception {
        try (BZip2CompressorInputStream bzIn = new BZip2CompressorInputStream(new BufferedInputStream(new FileInputStream(inputArchive)))) {
            try (FileOutputStream out = new FileOutputStream(outputFile, false)) {
                IOUtils.copy(bzIn, out);
            }
        }
    }

    /**
     * Copy file
     *
     * @param src Pointer to the source file
     * @param dst Destination file
     * @throws IOException If copy fail
     */
    public void copyFile(FileDescriptor src, File dst) throws IOException {
        try (FileInputStream in = new FileInputStream(src)) {
            try (FileOutputStream out = new FileOutputStream(dst, false)) {
                IOUtils.copy(in, out);
            }
        }
    }

}
