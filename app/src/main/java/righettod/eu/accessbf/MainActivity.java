package righettod.eu.accessbf;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.text.method.ScrollingMovementMethod;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import net.hockeyapp.android.CrashManager;
import net.hockeyapp.android.UpdateManager;

import org.apache.commons.compress.utils.IOUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Application main and single UI screen
 */
public class MainActivity extends Activity implements AdapterView.OnItemSelectedListener {

    public static final String LOG_TAG = "ACCESSBF";
    private static final int READ_DOCUMENT_REQUEST_CODE = 1;
    private String selectedPasswordDictionary = null;
    private File selectedPasswordDictionaryFilePath = null;
    private File dictionariesStorageFolder = null;
    private Protocol selectedProtocol = Protocol.SSH;
    private final List<AsyncTask<Object, Integer[], List<String>>> credentialProbingTaskList = new ArrayList<>();

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


        //Check if WIFI is enabled => Mandatory
        WifiManager wifi = (WifiManager) getSystemService(WIFI_SERVICE);
        if (!wifi.isWifiEnabled()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("WIFI is not enabled, application will be closed !").setTitle("Warning").setPositiveButton("OK", (DialogInterface dialog, int id) -> {
                        MainActivity.this.finish();
                    }
            );
            AlertDialog dialog = builder.create();
            dialog.show();
        }

        //Ensure that the user have the authorization of the network owner before to use the application
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Do you have an explicit authorization of the network owner to perform this brute force operation ?").setTitle("Legal Warning").setPositiveButton("Yes", (DialogInterface dialog, int id) -> {
                }
        ).setNegativeButton("No", (DialogInterface dialog, int id) -> MainActivity.this.finish());
        AlertDialog dialog = builder.create();
        dialog.show();

        //Init the storage location of the dictionaries
        this.dictionariesStorageFolder = new File(getApplicationContext().getFilesDir(), "dictionaries");
        if (!this.dictionariesStorageFolder.exists()) {
            CharSequence msg = "Storage location of the dictionaries successfully created !";
            if (!this.dictionariesStorageFolder.mkdirs()) {
                msg = "Storage location of the dictionaries creation failed !";
            }
            Toast toast = Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG);
            toast.show();
        }

        //Fill the dictionary selection component: https://developer.android.com/guide/topics/ui/controls/spinner.html
        Spinner spinner = (Spinner) findViewById(R.id.dicoSpinner);
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.password_dictionaries, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);

        //Add dictionary download button event handler
        final Button buttonDownloadDico = (Button) findViewById(R.id.buttonDownloadDico);
        buttonDownloadDico.setOnClickListener((View v) -> {
                    //Retrieve the dico download URL from string resources if it is not a LocalCustom dico
                    if ("Local...".equalsIgnoreCase(MainActivity.this.selectedPasswordDictionary)) {
                        //See https://developer.android.com/guide/topics/providers/document-provider.html
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        // Filter to only show results that can be "opened", such as a file (as opposed to a list of contacts or timezones)
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        //Want only data available on device and select only one item
                        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, false);
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
                        // Filter to show only text files, using the text MIME data type.
                        intent.setType("text/*");
                        startActivityForResult(intent, READ_DOCUMENT_REQUEST_CODE);
                    } else {
                        //Retrieve the URL
                        int id = MainActivity.this.getResources().getIdentifier(MainActivity.this.selectedPasswordDictionary, "string", MainActivity.this.getPackageName());
                        String dicoUrl = MainActivity.this.getResources().getString(id);
                        Uri dicoUri = Uri.parse(dicoUrl);
                        //Initiate the download of the file to the external storage through the Android Download Manager
                        // Create request for android download manager
                        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                        DownloadManager.Request request = new DownloadManager.Request(dicoUri);
                        //Set the local destination for the downloaded file to a path within the application's external files directory (will be moved after download)
                        String fileName = dicoUrl.substring(dicoUrl.lastIndexOf('/') + 1, dicoUrl.length());
                        String fileNameExtension = fileName.substring(fileName.lastIndexOf('.') + 1);
                        fileName = MainActivity.this.selectedPasswordDictionary + "." + fileNameExtension;
                        request.setDestinationInExternalFilesDir(MainActivity.this, Environment.DIRECTORY_DOWNLOADS, fileName);
                        //Setting title of request
                        String msg = "Download and process dico named '" + fileName + "'";
                        request.setTitle(msg);
                        //Enqueue download and save into a reference identifier
                        long currentDicoDownloadReference = downloadManager.enqueue(request);
                        //Start dedicated receiver
                        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
                        File targetFilePath = new File(MainActivity.this.dictionariesStorageFolder, MainActivity.this.derivateDicoFilename(fileName));
                        DownloadReceiver downloadReceiver = new DownloadReceiver(currentDicoDownloadReference, targetFilePath.getAbsolutePath());
                        registerReceiver(downloadReceiver, filter);
                    }
                }
        );

        //Add stop BF button event handler
        final Button buttonStop = (Button) findViewById(R.id.buttonStop);
        buttonStop.setOnClickListener((View v) -> {
                    try {
                        //Lock IU action components
                        findViewById(R.id.buttonStart).setEnabled(false);
                        findViewById(R.id.buttonStop).setEnabled(false);
                        findViewById(R.id.buttonDownloadDico).setEnabled(false);
                        findViewById(R.id.dicoSpinner).setEnabled(false);
                        findViewById(R.id.editIPPort).setEnabled(false);
                        findViewById(R.id.editUsername).setEnabled(false);
                        findViewById(R.id.bf_ssh).setEnabled(false);
                        findViewById(R.id.bf_smb).setEnabled(false);
                        findViewById(R.id.bf_ftp).setEnabled(false);

                        //Cancel all tasks
                        int cancelledTaskCount = 0;
                        if (!MainActivity.this.credentialProbingTaskList.isEmpty()) {
                            for (AsyncTask t : MainActivity.this.credentialProbingTaskList) {
                                if (t.getStatus() != AsyncTask.Status.FINISHED && !t.isCancelled()) {
                                    t.cancel(true);
                                    cancelledTaskCount++;
                                }
                            }
                        }
                        TextView textProgressInfo = (TextView) findViewById(R.id.textProgressInfo);
                        textProgressInfo.setText(cancelledTaskCount + " task(s) cancelled");
                    } finally {
                        //Unlock IU action components
                        findViewById(R.id.buttonStart).setEnabled(true);
                        findViewById(R.id.buttonStop).setEnabled(true);
                        findViewById(R.id.buttonDownloadDico).setEnabled(true);
                        findViewById(R.id.dicoSpinner).setEnabled(true);
                        findViewById(R.id.editIPPort).setEnabled(true);
                        findViewById(R.id.editUsername).setEnabled(true);
                        findViewById(R.id.bf_ssh).setEnabled(true);
                        findViewById(R.id.bf_smb).setEnabled(true);
                        findViewById(R.id.bf_ftp).setEnabled(true);
                    }
                }
        );

        //Add start BF button event handler
        final Button buttonStart = (Button) findViewById(R.id.buttonStart);
        buttonStart.setOnClickListener((View v) ->{
                try {
                    //Check that a password dictionary is selected
                    if (MainActivity.this.selectedPasswordDictionaryFilePath == null || !MainActivity.this.selectedPasswordDictionaryFilePath.exists()) {
                        NotificationUtil.showMessageDialog(MainActivity.this, "A dictionary must be selected !", "Warning");
                        return;
                    }
                    //Check that a target is specified
                    CharSequence target = ((TextView) findViewById(R.id.editIPPort)).getText();
                    if (target == null || target.toString().trim().isEmpty()) {
                        NotificationUtil.showMessageDialog(MainActivity.this, "A target must be specified !", "Warning");
                        return;
                    }
                    //Check that a login is specified
                    CharSequence login = ((TextView) findViewById(R.id.editUsername)).getText();
                    if (login == null || login.toString().trim().isEmpty()) {
                        NotificationUtil.showMessageDialog(MainActivity.this, "A login must be specified !", "Warning");
                        return;
                    }

                    //Load the dictionary's password entries
                    List<String> dictionaryEntries = MainActivity.this.loadDictionary(MainActivity.this.selectedPasswordDictionaryFilePath);

                    //Create and launch tasks
                    TextView textProgressInfo = (TextView) findViewById(R.id.textProgressInfo);
                    TextView textResult = (TextView) findViewById(R.id.textResult);
                    CredentialProbingTask task = new CredentialProbingTask(MainActivity.this.selectedProtocol, textProgressInfo, textResult, findViewById(R.id.dicoSpinner), findViewById(R.id.buttonDownloadDico), findViewById(R.id.buttonStart), findViewById(R.id.editIPPort), findViewById(R.id.editUsername), findViewById(R.id.bf_ftp), findViewById(R.id.bf_smb), findViewById(R.id.bf_ssh));
                    Object[] params = new Object[]{target.toString(), login.toString(), dictionaryEntries};
                    MainActivity.this.credentialProbingTaskList.clear();
                    MainActivity.this.credentialProbingTaskList.add(task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params));
                    textProgressInfo.setText("Brute force started...");
                } catch (Exception e) {
                    NotificationUtil.showMessageDialog(MainActivity.this, "Error during brute force operation:" + e.getMessage(), "Error");
                }

            }
        );

        //Handle action to copy result on clipboard when result text view is pressed
        final TextView resultTextView = (TextView) findViewById(R.id.textResult);
        resultTextView.setMovementMethod(new ScrollingMovementMethod());
        resultTextView.setOnTouchListener((View v, MotionEvent event) ->{
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("BF Result", resultTextView.getText());
                    clipboard.setPrimaryClip(clip);
                    Toast toast = Toast.makeText(getApplicationContext(), "Brute force result copied into clipboard !", Toast.LENGTH_SHORT);
                    toast.show();
                }
                return false;
            }
        );

        //HockeyApp crash handler
        checkForUpdates();
    }

    /**
     * Handle the click on the radio button used to select to protocol to the target
     *
     * @param view View representing the radio button
     */
    public void onProtocolRadioButtonClicked(View view) {
        // Get a reference on the radio button clicked
        boolean checked = ((RadioButton) view).isChecked();

        // Check which radio button was clicked
        switch (view.getId()) {
            case R.id.bf_ftp:
                if (checked) {
                    this.selectedProtocol = Protocol.FTP;
                }
                break;
            case R.id.bf_smb:
                if (checked) {
                    this.selectedProtocol = Protocol.SMB;
                }
                break;
            case R.id.bf_ssh:
                if (checked) {
                    this.selectedProtocol = Protocol.SSH;
                }
                break;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == READ_DOCUMENT_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                //Define file information
                this.selectedPasswordDictionaryFilePath = new File(this.dictionariesStorageFolder, "CustomDict.txt");
                this.selectedPasswordDictionary = "CustomDict";
                //Get file location and create a local file with the file "remote" content
                TextView textProgressInfo = (TextView) findViewById(R.id.textProgressInfo);
                try {
                    this.copyFile(resultData.getData(), this.selectedPasswordDictionaryFilePath);
                    textProgressInfo.setText("Dictionary loaded (" + this.loadDictionary(this.selectedPasswordDictionaryFilePath).size() + " passwords)");
                    findViewById(R.id.buttonStart).setEnabled(true);
                    findViewById(R.id.buttonStop).setEnabled(true);
                } catch (IOException ioe) {
                    textProgressInfo.setText("Cannot load the dictionary: " + ioe.getMessage());
                }

            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        TextView textProgressInfo = (TextView) findViewById(R.id.textProgressInfo);
        // An item was selected. We retrieve the selected item using
        this.selectedPasswordDictionary = parent.getItemAtPosition(pos).toString();
        //Update Download button text to represent the real action that will be performed when clicked
        Button downloadButton = (Button) findViewById(R.id.buttonDownloadDico);
        downloadButton.setText(("Local...".equalsIgnoreCase(MainActivity.this.selectedPasswordDictionary) ? "Load Dictionary" : "Download Dictionary"));
        if (!"Local...".equalsIgnoreCase(MainActivity.this.selectedPasswordDictionary)) {
            //Show in the information label if the selected dict is locally available of not
            this.selectedPasswordDictionaryFilePath = new File(this.dictionariesStorageFolder, this.derivateDicoFilename(this.selectedPasswordDictionary));
            if (!this.selectedPasswordDictionaryFilePath.exists()) {
                textProgressInfo.setText("Dict must be downloaded !");
                findViewById(R.id.buttonStart).setEnabled(false);
                findViewById(R.id.buttonStop).setEnabled(false);
            } else {
                int passCount;
                try {
                    passCount = this.countDictionaryEntries(this.selectedPasswordDictionaryFilePath);
                } catch (IOException e) {
                    passCount = -1;
                    Toast toast = Toast.makeText(getApplicationContext(), "Cannot load the dictionary: " + e.getMessage(), Toast.LENGTH_LONG);
                    toast.show();
                }
                textProgressInfo.setText("Dict is locally available (" + passCount + " passwords)");
                findViewById(R.id.buttonStart).setEnabled(true);
                findViewById(R.id.buttonStop).setEnabled(true);
            }
        } else {
            textProgressInfo.setText("Dict must be locally selected !");
            findViewById(R.id.buttonStart).setEnabled(false);
            findViewById(R.id.buttonStop).setEnabled(false);
        }

    }

    /**
     * {@inheritDoc}
     */
    public void onNothingSelected(AdapterView<?> parent) {
        this.selectedPasswordDictionary = null;
        this.selectedPasswordDictionaryFilePath = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onResume() {
        super.onResume();
        //HockeyApp crash handler
        checkForCrashes();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPause() {
        super.onPause();
        //HockeyApp crash handler
        unregisterManagers();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        //HockeyApp crash handler
        unregisterManagers();
    }


    /**
     * Load the content of a dictionary in a list of string
     *
     * @param dict Path to the dictionary to load
     * @return The list of password from the source dictionary
     * @throws IOException If dictionary cannot be read
     */
    private List<String> loadDictionary(File dict) throws IOException {
        List<String> dictEntries = new ArrayList<>();

        try (BufferedReader r = new BufferedReader(new FileReader(dict))) {
            String pass;
            while (r.ready()) {
                pass = r.readLine();
                if (!pass.trim().isEmpty()) {
                    dictEntries.add(pass);
                }
            }
        }

        return dictEntries;
    }

    /**
     * Compute the number of valid entries contained in a dictionary
     *
     * @param dict Path to the dictionary to load
     * @return The number of valid entries
     * @throws IOException If dictionary cannot be read
     */
    private int countDictionaryEntries(File dict) throws IOException {
        int count = 0;

        try (BufferedReader r = new BufferedReader(new FileReader(dict))) {
            String pass;
            while (r.ready()) {
                pass = r.readLine();
                if (!pass.trim().isEmpty()) {
                    count++;
                }
            }
        }

        return count;
    }

    /**
     * Derivate the name of the dictionary filename in the app dico store
     *
     * @param filename Source filename
     * @return Dictionary filename in the app dico store
     */
    private String derivateDicoFilename(String filename) {
        return filename.toLowerCase().replace(".bz2", "").replace(".txt", "").concat(".txt").trim();
    }

    /**
     * Copy file
     *
     * @param src Pointer to the source file
     * @param dst Destination file
     * @throws IOException If copy fail
     */
    private void copyFile(Uri src, File dst) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(src)) {
            try (FileOutputStream out = new FileOutputStream(dst, false)) {
                IOUtils.copy(in, out);
            }
        }
    }

    /**
     * HockeyApp app management event handler
     */
    private void checkForCrashes() {
        CrashManager.register(this);
    }

    /**
     * HockeyApp app management event handler
     */
    private void checkForUpdates() {
        // Remove this for store builds!
        UpdateManager.register(this);
    }

    /**
     * HockeyApp app management event handler
     */
    private void unregisterManagers() {
        UpdateManager.unregister();
    }


}
