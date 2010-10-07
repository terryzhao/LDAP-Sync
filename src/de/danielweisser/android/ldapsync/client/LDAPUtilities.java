package de.danielweisser.android.ldapsync.client;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.RootDSE;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;

import de.danielweisser.android.ldapsync.R;
import de.danielweisser.android.ldapsync.authenticator.LDAPAuthenticatorActivity;
import de.danielweisser.android.ldapsync.syncadapter.SyncService;

/**
 * Provides utility methods for communicating with the server.
 */
public class LDAPUtilities {
	private static final String TAG = "LDAPUtilities";

	/**
	 * Executes the network requests on a separate thread.
	 * 
	 * @param runnable
	 *            The runnable instance containing network operations to be executed.
	 */
	public static Thread performOnBackgroundThread(final Runnable runnable) {
		final Thread t = new Thread() {
			@Override
			public void run() {
				try {
					runnable.run();
				} finally {
				}
			}
		};
		t.start();
		return t;
	}

	/**
	 * Sends the authentication response from server back to the caller main UI thread through its handler.
	 * 
	 * @param result
	 *            The boolean holding authentication result
	 * @param handler
	 *            The main UI thread's handler instance.
	 * @param context
	 *            The caller Activity's context.
	 */
	private static void sendResult(final String[] baseDNs, final Boolean result, final Handler handler, final Context context) {
		if (handler == null || context == null) {
			return;
		}
		handler.post(new Runnable() {
			public void run() {
				((LDAPAuthenticatorActivity) context).onAuthenticationResult(baseDNs, result);
			}
		});
	}

	/**
	 * Obtains a list of all contacts from the LDAP Server.
	 * 
	 * @param ldapServer
	 *            The LDAP server data
	 * @param baseDN
	 *            The baseDN that will be used for the search
	 * @param searchFilter
	 *            The search filter
	 * @param mappingBundle
	 *            A bundle of all LDAP attributes that are queried
	 * @param mLastUpdated
	 *            Date of the last update
	 * @param context
	 *            The caller Activity's context
	 * @return List of all LDAP contacts
	 */
	public static List<Contact> fetchContacts(final LDAPServerInstance ldapServer, final String baseDN, final String searchFilter, final Bundle mappingBundle,
			final Date mLastUpdated, final Context context) {
		final ArrayList<Contact> friendList = new ArrayList<Contact>();
		LDAPConnection connection = null;
		try {
			connection = ldapServer.getConnection();
			SearchResult searchResult = connection.search(baseDN, SearchScope.SUB, searchFilter, getUsedAttributes(mappingBundle));
			Log.i(TAG, searchResult.getEntryCount() + " entries returned.");
			for (SearchResultEntry e : searchResult.getSearchEntries()) {
				Contact u = Contact.valueOf(e, mappingBundle);
				if (u != null) {
					friendList.add(u);
				}
			}
		} catch (LDAPException e) {
			Log.v(TAG, "LDAPException on fetching contacts", e);
			NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
			int icon = R.drawable.icon;
			CharSequence tickerText = "Error on LDAP Sync";
			long when = System.currentTimeMillis();
			Notification notification = new Notification(icon, tickerText, when);
			Intent notificationIntent = new Intent(context, SyncService.class);
			PendingIntent contentIntent = PendingIntent.getService(context, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);
			notification.setLatestEventInfo(context, tickerText, e.getMessage(), contentIntent);
			notification.flags = Notification.FLAG_AUTO_CANCEL;
			mNotificationManager.notify(0, notification);
			return null;
		} finally {
			if (connection != null) {
				connection.close();
			}
		}

		return friendList;
	}

	private static String[] getUsedAttributes(Bundle mappingBundle) {
		ArrayList<String> ldapAttributes = new ArrayList<String>();
		String[] ldapArray = new String[mappingBundle.size()];
		for (String key : mappingBundle.keySet()) {
			ldapAttributes.add(mappingBundle.getString(key));
		}
		ldapArray = ldapAttributes.toArray(ldapArray);
		return ldapArray;
	}

	/**
	 * Attempts to authenticate the user credentials on the server.
	 * 
	 * @param ldapServer
	 *            The LDAP server data
	 * @param handler
	 *            The main UI thread's handler instance.
	 * @param context
	 *            The caller Activity's context
	 * @return Thread The thread on which the network mOperations are executed.
	 */
	public static Thread attemptAuth(final LDAPServerInstance ldapServer, final Handler handler, final Context context) {
		final Runnable runnable = new Runnable() {
			public void run() {
				authenticate(ldapServer, handler, context);
			}
		};
		// run on background thread.
		return LDAPUtilities.performOnBackgroundThread(runnable);
	}

	/**
	 * Tries to authenticate against the LDAP server and
	 * 
	 * @param ldapServer
	 *            The LDAP server data
	 * @param handler
	 *            The handler instance from the calling UI thread.
	 * @param context
	 *            The context of the calling Activity.
	 * @return {code false} if the authentication fails, {code true} otherwise
	 */
	public static boolean authenticate(LDAPServerInstance ldapServer, Handler handler, final Context context) {
		LDAPConnection connection = null;
		try {
			connection = ldapServer.getConnection();

			if (connection != null) {
				RootDSE s = connection.getRootDSE();
				String[] baseDNs = s.getNamingContextDNs();

				sendResult(baseDNs, true, handler, context);
				return true;
			}
		} catch (LDAPException e) {
			Log.e(TAG, "Error authenticating", e);
		} finally {
			if (connection != null) {
				connection.close();
			}
		}
		sendResult(null, false, handler, context);
		return false;
	}
}
