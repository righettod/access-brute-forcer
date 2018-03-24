package righettod.eu.accessbf;


import android.content.Context;
import android.os.AsyncTask;
import android.os.Vibrator;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


/**
 * Task in charge of testing a login + a set of passwords against a target host and port.
 */
class CredentialProbingTask extends AsyncTask<Object, Integer[], List<String>> {

    /**
     * UI component to update for the progression
     */
    private TextView textProgressInfo = null;

    /**
     * UI component to update for the result
     */
    private TextView textResult = null;

    /**
     * UI component to update
     */
    private View[] actionViews = null;


    /**Protocol Protocol to use for the credential probing*/
    private Protocol protocol = null;

    /**
     * Constructor
     * @param protocol Protocol to use for the credential probing
     * @param textProgressInfo UI component to update to provide text feedback
     * @param textResult       UI component to update to provide text feedback about result
     * @param actionViews      List of action view to disable and re-enable during and after the processing
     */
    public CredentialProbingTask(Protocol protocol, TextView textProgressInfo, TextView textResult, View... actionViews) {
        this.protocol = protocol;
        this.textProgressInfo = textProgressInfo;
        this.actionViews = actionViews;
        this.textResult = textResult;
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
        CopyOnWriteArrayList<String> validCreds = new CopyOnWriteArrayList<>();
        try {
            //Get execution parameters
            final String target = (String) params[0];
            final String login = (String) params[1];
            final List<String> passwords = (List<String>) params[2];


            //Add empty password and login variations
            passwords.add("");
            passwords.add(" ");
            passwords.add(login);
            passwords.add(login.toUpperCase());
            passwords.add(login.toLowerCase());

            //Test the password list
            int passwordsCount = passwords.size();
            final ProtocolUtil protocolUtil = new ProtocolUtil(target,login);
            final CopyOnWriteArrayList<Integer> tryCountHolder = new CopyOnWriteArrayList<>();
            tryCountHolder.add(0);
            passwords.parallelStream().forEach(password -> {
                try {
                    //Apply effective probing only if password has not already been found and task has not been cancelled
                    if(validCreds.isEmpty() && !this.isCancelled()){
                        boolean isValidPassword;
                        switch(this.protocol){
                            case SMB:
                                isValidPassword = protocolUtil.isValidPasswordForSMB(password);
                                break;
                            case SSH:
                                isValidPassword = protocolUtil.isValidPasswordForSSH(password);
                                break;
                            case FTP:
                                isValidPassword = protocolUtil.isValidPasswordForFTP(password);
                                break;
                            default:
                                isValidPassword = false;
                                break;
                        }
                        if (isValidPassword) {
                            validCreds.add("<br><strong>Host</strong> &rArr; " + target + "<br><strong>Protocol</strong> &rArr; " + protocol + "<br><strong>Login</strong> &rArr; [" + login + "]<br><strong>Password</strong> &rArr; [" + password + "]");
                        }
                    }
                } catch (Exception ignore) {
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
            msgInfo = "<font color='#06a01f'>Found valid credential, check out below between square brackets. Hold and release the zone to copy credential data into clipboard.</font>";
            //Render the list of credentials found
            StringBuilder buffer = new StringBuilder();
            result.forEach(r -> buffer.append(r).append("<br>"));
            this.textResult.setText(Html.fromHtml(buffer.toString(), Html.FROM_HTML_MODE_LEGACY));
            //Notify via vibration and top bar notification that credentials has been found
            NotificationUtil.sendNotification(this.textResult.getContext(), "Found valid credential !");
            Vibrator v = (Vibrator) this.textResult.getContext().getSystemService(Context.VIBRATOR_SERVICE);
            v.vibrate(2000);
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
