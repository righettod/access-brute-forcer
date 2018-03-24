package righettod.eu.accessbf;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import org.apache.commons.net.ftp.FTPClient;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

/**
 * Handle communication using the different supported protocols
 */

public class ProtocolUtil {

    /** Timeout when connect i tried wit the target whatever the protocol used - 5 seconds*/
    private static final int CONNECTION_TIMEOUT = 5000;

    /**
     * Host of the brute force destination
     */
    private final String host;

    /**
     * Port of the brute force destination
     */
    private final int port;

    /**
     * Login to use for the brute force
     */
    private final String login;


    /**
     * @param target Host and optional port of the destination
     * @param login  Login to use for the brute force
     * @throws UnknownHostException If hostname cannot be resolved in case of IPV4/V6 provided
     */
    public ProtocolUtil(String target, String login) throws UnknownHostException {
        System.setProperty("jcifs.smb.client.responseTimeout", Integer.toString(CONNECTION_TIMEOUT));
        System.setProperty("jcifs.smb.client.soTimeout", System.getProperty("jcifs.smb.client.responseTimeout"));
        //Extract host and port
        if (target.contains(":")) {
            String[] parts = target.trim().split(":");
            this.host = parts[0].trim();
            this.port = Integer.parseInt(parts[1].trim());
        } else {
            this.host = target.trim();
            this.port = -1;
        }
        this.login = login;
    }

    /**
     * Test if the password provided is a valid credential set when combined with the login for an authentication using SMB protocol
     *
     * @param password Password to test
     * @return TRUE if connection succeed, FALSE otherwise
     */
    public boolean isValidPasswordForSMB(String password) {
        boolean isValid;
        String loginToUse = this.login;
        String domainToUse = ".";// . mean target machine and not a domain
        String targetHost = this.host + ((this.port == -1) ? "" : ":".concat(Integer.toString(port)));

        //Detect if a domain is specified in the login
        if (login.contains("\\")) {
            String[] part = login.split("\\\\");
            domainToUse = part[0].trim();
            loginToUse = part[1].trim();
        }

        try {
            //Test the credentials against the target
            //String pwdUrlEncoded = password.replace("%", "%25").replace("@", "%40").replace(":", "%3A").replace("#", "%23");
            String url = "smb://" + targetHost + "/";
            NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(domainToUse, loginToUse, password);
            SmbFile testFile = new SmbFile(url, auth);
            isValid = (testFile != null && testFile.list().length > 0);
        } catch (SmbException | MalformedURLException exp) {
            //Explicitly ignore this exception because it's a brute force here...
            isValid = false;
        }

        return isValid;
    }


    /**
     * Test if the password provided is a valid credential set when combined with the login for an authentication using SSH protocol
     *
     * @param password Password to test
     * @return TRUE if connection succeed, FALSE otherwise
     */
    public boolean isValidPasswordForSSH(String password) {
        boolean isValid;
        Session session = null;
        int sshPort = (this.port == -1) ? 22 : this.port;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(this.login, this.host, sshPort);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setPassword(password);
            session.connect(CONNECTION_TIMEOUT);
            isValid = session.isConnected();
        } catch (JSchException ignored) {
            //Explicitly ignore this exception because it's a brute force here...
            isValid = false;
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }

        return isValid;
    }

    /**
     * Test if the password provided is a valid credential set when combined with the login for an authentication using FTP protocol
     *
     * @param password Password to test
     * @return TRUE if connection succeed, FALSE otherwise
     */
    public boolean isValidPasswordForFTP(String password) {
        boolean isValid;
        FTPClient ftp = null;
        int ftpPort = (this.port == -1) ? 21 : this.port;

        try {
            ftp = new FTPClient();
            ftp.setConnectTimeout(CONNECTION_TIMEOUT);
            ftp.connect(this.host, ftpPort);
            isValid = ftp.login(this.login, password);
            if (isValid) {
                ftp.logout();
            }
        } catch (IOException ignored) {
            //Explicitly ignore this exception because it's a brute force here...
            isValid = false;
        } finally {
            if (ftp != null && ftp.isConnected()) {
                try {
                    ftp.disconnect();
                } catch (IOException ignored) {
                }

            }
        }

        return isValid;
    }


}
