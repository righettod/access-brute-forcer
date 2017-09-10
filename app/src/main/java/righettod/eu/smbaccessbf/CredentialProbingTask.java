package righettod.eu.smbaccessbf;


import android.os.AsyncTask;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;


/**
 * Task in charge of testing a login + a set of passwords against a target host and port.
 */
class CredentialProbingTask extends AsyncTask<Object, Integer[], List<String>> {

    /**
     * UI component to update
     */
    private TextView textProgressInfo = null;
    private TextView textResult = null;
    private View[] actionViews = null;

    /**
     * Constructor
     *
     * @param textProgressInfo UI component to update to provide text feedback
     * @param textResult       UI component to update to provide text feedback about result
     * @param actionViews      List of action view to disable and re-enable during and after the processing
     */
    public CredentialProbingTask(TextView textProgressInfo, TextView textResult, View... actionViews) {
        this.textProgressInfo = textProgressInfo;
        this.actionViews = actionViews;
        this.textResult = textResult;
        System.setProperty("jcifs.smb.client.responseTimeout", "5000"); // default: 30000 ms
        System.setProperty("jcifs.smb.client.soTimeout", "5000"); // default: 35000 mss
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onPreExecute() {
        //Disable action view
        for (View b : this.actionViews) {
            b.setEnabled(false);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected List<String> doInBackground(Object... params) {
        List<String> validCreds = Collections.synchronizedList(new ArrayList<String>());
        try {
            //Get execution parameters
            String target = (String) params[0];
            String login = (String) params[1];
            List<String> passwords = (List<String>) params[2];
            int passwordsCount = passwords.size();

            //Detect if a domain is specified in the login
            String domain = ".";// . mean target machine and not a domain
            if (login.contains("\\")) {
                String[] part = login.split("\\\\");
                domain = part[0].trim();
                login = part[1].trim();
            }
            final String loginToUse = login;
            final String domainToUse = domain;

            //Add the empty password to the list
            passwords.add("");
            passwords.add(" ");

            //Test the password list
            final CopyOnWriteArrayList<Integer> tryCountHolder = new CopyOnWriteArrayList<>();
            tryCountHolder.add(0);
            passwords.parallelStream().forEach(password -> {
                try {
                    //Test the credentials against the target
                    //String pwdUrlEncoded = password.replace("%", "%25").replace("@", "%40").replace(":", "%3A").replace("#", "%23");
                    String url = "smb://" + target + "/";
                    NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(domainToUse, loginToUse, password);
                    SmbFile testFile = new SmbFile(url, auth);
                    if (testFile != null && testFile.list().length > 0) {
                        validCreds.add("[" + loginToUse + "] &rArr; [" + password + "]");
                    }
                } catch (SmbException | MalformedURLException exp) {
                    //Explicitly ignore this exception because it's a brute force here...
                } finally {
                    int counter = tryCountHolder.get(0) + 1;
                    tryCountHolder.set(0, counter);
                    publishProgress(new Integer[]{counter, passwordsCount});
                }
            });
        } catch (Exception e) {
            String msg = "Error during credentials probing: " + e.getMessage();
            Log.e(MainActivity.LOG_TAG, msg, e);
        }

        return validCreds;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onProgressUpdate(Integer[]... values) {
        int passwordTestedCount = values[0][0];
        int passwordTotalCount = values[0][1];
        int progressPercentage = (passwordTestedCount * 100) / passwordTotalCount;
        String msg = "Passwords dictionary tested at " + progressPercentage + "% (" + passwordTotalCount + " entries to test) ...";
        if (!msg.equalsIgnoreCase(this.textProgressInfo.getText().toString())) {
            this.textProgressInfo.setText(msg);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onPostExecute(List<String> result) {
        //Display result
        String msgInfo;
        if (result != null && !result.isEmpty()) {
            //Display that we have found a valid credential combination
            msgInfo = "<font color='#06a01f'>Found valid credential, check out below between square brackets !</font>";
            //Render the list of credentials found
            StringBuilder buffer = new StringBuilder();
            result.forEach(r -> buffer.append(r).append("<br>"));
            this.textResult.setText(Html.fromHtml(buffer.toString(), Html.FROM_HTML_MODE_LEGACY));
        } else {
            msgInfo = "<font color='red'>No valid credential found !</font>";
            this.textResult.setText("");
        }
        //Display the final result state info
        this.textProgressInfo.setText(Html.fromHtml(msgInfo, Html.FROM_HTML_MODE_LEGACY));
        //Disable action buttons
        for (View b : this.actionViews) {
            b.setEnabled(true);
        }
    }
}
