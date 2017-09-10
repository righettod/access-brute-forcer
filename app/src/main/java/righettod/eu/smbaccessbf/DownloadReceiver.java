package righettod.eu.smbaccessbf;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.util.Log;

/**
 * Receiver in charge of handling status and post download processing delegation of the file downloaded through the Android Download Manager.
 * In our case it's a password dictionary.
 */
public class DownloadReceiver extends BroadcastReceiver {

    private long currentDicoDownloadReference;
    private String targetFilePath;

    /**
     * Constructor
     */
    public DownloadReceiver(){
        this.currentDicoDownloadReference = -999;
    }

    /**
     * Constructor
     *
     * @param currentDicoDownloadReference ID given by the Android Download Manager and that must be managed by this handler
     * @param targetFilePath               Target path to witch the downloaded file must be moved to
     */
    public DownloadReceiver(long currentDicoDownloadReference, String targetFilePath) {
        this.currentDicoDownloadReference = currentDicoDownloadReference;
        this.targetFilePath = targetFilePath;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            //Check if the broadcast message is for our enqueued download
            long referenceId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (referenceId == this.currentDicoDownloadReference) {
                DownloadManager downloadManager = (DownloadManager) context.getSystemService(context.DOWNLOAD_SERVICE);
                //Retrieve the status of the downloaded file
                DownloadManager.Query downloadQuery = new DownloadManager.Query();
                //Set the query filter to our previously Enqueued download
                downloadQuery.setFilterById(this.currentDicoDownloadReference);
                //Query the download manager about downloads that have been requested.
                Cursor cursor = downloadManager.query(downloadQuery);
                if (cursor.moveToFirst()) {
                    //Get the download status
                    int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    int status = cursor.getInt(columnIndex);
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        //Get the downloaded file path
                        columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                        String filePath = cursor.getString(columnIndex);
                        //Send Intent to the Post download file processor Service
                        Intent downloadFileProcessIntent = new Intent(context, PostDownloadService.class);
                        downloadFileProcessIntent.putExtra(PostDownloadService.EXTRA_PARAM_DL_FILE_PATH, filePath);
                        downloadFileProcessIntent.putExtra(PostDownloadService.EXTRA_PARAM_TARGET_FILE_PATH, this.targetFilePath);
                        downloadFileProcessIntent.setAction(PostDownloadService.ACTION_DL_FILE_PROCESS);
                        context.startService(downloadFileProcessIntent);
                        NotificationUtil.sendNotification(context, "Dictionary download OK, pass to post processing...");
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        //Get fail reason
                        String reasonText = "";
                        int columnReason = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                        int reason = cursor.getInt(columnReason);
                        switch (reason) {
                            case DownloadManager.ERROR_CANNOT_RESUME:
                                reasonText = "ERROR_CANNOT_RESUME";
                                break;
                            case DownloadManager.ERROR_DEVICE_NOT_FOUND:
                                reasonText = "ERROR_DEVICE_NOT_FOUND";
                                break;
                            case DownloadManager.ERROR_FILE_ALREADY_EXISTS:
                                reasonText = "ERROR_FILE_ALREADY_EXISTS";
                                break;
                            case DownloadManager.ERROR_FILE_ERROR:
                                reasonText = "ERROR_FILE_ERROR";
                                break;
                            case DownloadManager.ERROR_HTTP_DATA_ERROR:
                                reasonText = "ERROR_HTTP_DATA_ERROR";
                                break;
                            case DownloadManager.ERROR_INSUFFICIENT_SPACE:
                                reasonText = "ERROR_INSUFFICIENT_SPACE";
                                break;
                            case DownloadManager.ERROR_TOO_MANY_REDIRECTS:
                                reasonText = "ERROR_TOO_MANY_REDIRECTS";
                                break;
                            case DownloadManager.ERROR_UNHANDLED_HTTP_CODE:
                                reasonText = "ERROR_UNHANDLED_HTTP_CODE";
                                break;
                            case DownloadManager.ERROR_UNKNOWN:
                                reasonText = "ERROR_UNKNOWN";
                                break;
                        }
                        NotificationUtil.sendNotification(context, "Dictionary download fail for reason '" + reasonText + "' !");
                    }
                }
            }

        } catch (Exception e) {
            String msg = "Error during the download file processing: " + e.getMessage();
            Log.e(MainActivity.LOG_TAG, msg, e);
            NotificationUtil.sendNotification(context, msg);
        }
    }


}
