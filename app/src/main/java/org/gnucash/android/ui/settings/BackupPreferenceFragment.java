/*
 * Copyright (c) 2012 Ngewi Fet <ngewif@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gnucash.android.ui.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceManager;
import android.text.InputType;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.MetadataChangeSet;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.export.ExportFormat;
import org.gnucash.android.export.Exporter;
import org.gnucash.android.export.GitHubExporter;
import org.gnucash.android.importer.ImportAsyncTask;
import org.gnucash.android.util.BackupManager;
import org.gnucash.android.util.TokenDecryptor;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;


/**
 * Fragment for displaying general preferences
 * @author Ngewi Fet <ngewif@gmail.com>
 *
 */
public class BackupPreferenceFragment extends PreferenceFragmentCompat implements
		Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener {

	public static final int REQUEST_RESOLVE_CONNECTION = 0x12;

	/**
	 * Request code for the backup file where to save backups
	 */
	private static final int REQUEST_BACKUP_FILE = 0x13;

	/**
	 * Testing app key for DropBox API
	 */
	final static public String DROPBOX_APP_KEY      = "dhjh8ke9wf05948";

	/**
	 * Testing app secret for DropBox API
	 */
	final static public String DROPBOX_APP_SECRET   = "h2t9fphj3nr4wkw";

	/**
	 * String for tagging log statements
	 */
	public static final String LOG_TAG = "BackupPrefFragment";

	/**
	 * Client for Google Drive Sync
	 */
	public static GoogleApiClient mGoogleApiClient;


	@Override
	public void onCreatePreferences(Bundle bundle, String s) {
		addPreferencesFromResource(R.xml.fragment_backup_preferences);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		ActionBar actionBar = ((AppCompatActivity)getActivity()).getSupportActionBar();
		actionBar.setHomeButtonEnabled(true);
		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setTitle(R.string.title_backup_prefs);

		mGoogleApiClient = getGoogleApiClient(getActivity());
		
	}	
	
	@Override
	public void onResume() {
		super.onResume();
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

		String keyDefaultEmail = getString(R.string.key_default_export_email);		
		Preference pref = findPreference(keyDefaultEmail);
		String defaultEmail = sharedPrefs.getString(keyDefaultEmail, null);
		if (defaultEmail != null && !defaultEmail.trim().isEmpty()){
			pref.setSummary(defaultEmail);			
		}
		pref.setOnPreferenceChangeListener(this);

        String keyDefaultExportFormat = getString(R.string.key_default_export_format);
        pref = findPreference(keyDefaultExportFormat);
        String defaultExportFormat = sharedPrefs.getString(keyDefaultExportFormat, null);
        if (defaultExportFormat != null && !defaultExportFormat.trim().isEmpty()){
            pref.setSummary(defaultExportFormat);
        }
        pref.setOnPreferenceChangeListener(this);

		pref = findPreference(getString(R.string.key_restore_backup));
		pref.setOnPreferenceClickListener(this);

		pref = findPreference(getString(R.string.key_create_backup));
		pref.setOnPreferenceClickListener(this);

		pref = findPreference(getString(R.string.key_backup_location));
		pref.setOnPreferenceClickListener(this);
		String defaultBackupLocation = BackupManager.getBookBackupFileUri(BooksDbAdapter.getInstance().getActiveBookUID());
		if (defaultBackupLocation != null){
			pref.setSummary(Uri.parse(defaultBackupLocation).getAuthority());
		}

		// GitHub export settings: show the stored values as summaries
		String[] githubKeys = {
				getString(R.string.key_github_repo),
				getString(R.string.key_github_branch),
				getString(R.string.key_github_path),
				getString(R.string.key_github_token)
		};
		for (String key : githubKeys) {
			pref = findPreference(key);
			pref.setOnPreferenceChangeListener(this);
			String value = sharedPrefs.getString(key, null);
			if (value != null && !value.trim().isEmpty())
				pref.setSummary(summaryForGitHubPreference(key, value));
		}

		pref = findPreference(getString(R.string.key_github_download_token));
		pref.setOnPreferenceClickListener(this);

		pref = findPreference(getString(R.string.key_github_export_now));
		pref.setOnPreferenceClickListener(this);
	}

	/**
	 * Summary text for a GitHub preference. The access token is never shown in clear text.
	 */
	private String summaryForGitHubPreference(String key, String value) {
		if (key.equals(getString(R.string.key_github_token)))
			return "••••••••";
		return value;
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		String key = preference.getKey();

		if (key.equals(getString(R.string.key_restore_backup))){
			restoreBackup();
		}

		if (key.equals(getString(R.string.key_backup_location))){
			Intent createIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
			createIntent.setType("*/*");
			createIntent.addCategory(Intent.CATEGORY_OPENABLE);
			String bookName = BooksDbAdapter.getInstance().getActiveBookDisplayName();
			createIntent.putExtra(Intent.EXTRA_TITLE, Exporter.sanitizeFilename(bookName)+ "_" + getString(R.string.label_backup_filename));
			startActivityForResult(createIntent, REQUEST_BACKUP_FILE);
		}

		if (key.equals(getString(R.string.key_github_download_token))){
			askPasswordAndDownloadToken(preference);
		}

		if (key.equals(getString(R.string.key_github_export_now))){
			exportToGitHub(preference);
		}

		if (key.equals(getString(R.string.key_create_backup))){
			// Run the export off the UI thread: the XML export of a large book can take
			// a while and must not block (or be cut short by) the main thread.
			new CreateBackupTask(this, preference).execute();
		}

		return false;
	}

	/**
	 * Asks for the password of the encrypted token file and then downloads, decrypts and stores
	 * the token. The password is only kept in memory for the duration of the operation.
	 */
	private void askPasswordAndDownloadToken(final Preference preference) {
		final EditText passwordInput = new EditText(getActivity());
		passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
		passwordInput.setHint(R.string.hint_github_token_password);
		passwordInput.setSingleLine(true);

		// padding around the field so it does not touch the dialog edges
		int padding = (int) (20 * getResources().getDisplayMetrics().density);
		FrameLayout container = new FrameLayout(getActivity());
		container.setPadding(padding, padding / 2, padding, 0);
		container.addView(passwordInput);

		new android.support.v7.app.AlertDialog.Builder(getActivity())
				.setTitle(R.string.title_github_token_password)
				.setView(container)
				.setNegativeButton(R.string.btn_cancel, null)
				.setPositiveButton(R.string.btn_github_token_decrypt, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						String password = passwordInput.getText().toString();
						if (!password.isEmpty())
							new DownloadTokenTask(BackupPreferenceFragment.this, preference, password).execute();
					}
				})
				.show();
	}

	/**
	 * Downloads and decrypts the token file off the UI thread (PBKDF2 with 200 000 iterations is
	 * deliberately slow) and puts the result into the "access token" preference.
	 */
	private static class DownloadTokenTask extends AsyncTask<Void, Void, String> {
		private final WeakReference<BackupPreferenceFragment> mFragmentRef;
		private final WeakReference<Preference> mPreferenceRef;
		private final Context mAppContext;
		private final String mPassword;
		private String mError;

		DownloadTokenTask(BackupPreferenceFragment fragment, Preference preference, String password) {
			mFragmentRef = new WeakReference<>(fragment);
			mPreferenceRef = new WeakReference<>(preference);
			mAppContext = fragment.getActivity().getApplicationContext();
			mPassword = password;
		}

		@Override
		protected void onPreExecute() {
			Preference preference = mPreferenceRef.get();
			if (preference != null)
				preference.setEnabled(false);
		}

		/**
		 * @return the decrypted token, or {@code null} if it failed (see {@link #mError})
		 */
		@Override
		protected String doInBackground(Void... params) {
			try {
				byte[] fileContent = GitHubExporter.downloadTokenFile();
				return TokenDecryptor.decrypt(fileContent, mPassword);
			} catch (IOException | GeneralSecurityException | RuntimeException e) {
				Log.e(LOG_TAG, "Could not get the GitHub token", e);
				mError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
				return null;
			}
		}

		@Override
		protected void onPostExecute(String token) {
			Preference downloadPref = mPreferenceRef.get();
			if (downloadPref != null)
				downloadPref.setEnabled(true);

			if (token == null) {
				Toast.makeText(mAppContext,
						mAppContext.getString(R.string.toast_github_token_failed, mError),
						Toast.LENGTH_LONG).show();
				return;
			}

			String tokenKey = mAppContext.getString(R.string.key_github_token);
			BackupPreferenceFragment fragment = mFragmentRef.get();
			Preference tokenPref = fragment == null ? null : fragment.findPreference(tokenKey);
			if (tokenPref instanceof EditTextPreference) {
				// persists the value and refreshes the field the user sees
				((EditTextPreference) tokenPref).setText(token);
				tokenPref.setSummary(fragment.summaryForGitHubPreference(tokenKey, token));
			} else {
				// the screen was closed meanwhile: store the token directly
				PreferenceManager.getDefaultSharedPreferences(mAppContext)
						.edit().putString(tokenKey, token).apply();
			}
			Toast.makeText(mAppContext, R.string.toast_github_token_loaded, Toast.LENGTH_SHORT).show();
		}
	}

	/**
	 * Uploads a backup of the active book to the configured GitHub repository.
	 * Settings are read from the default shared preferences.
	 */
	private void exportToGitHub(Preference preference) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		GitHubExporter exporter = new GitHubExporter(
				prefs.getString(getString(R.string.key_github_repo), ""),
				prefs.getString(getString(R.string.key_github_branch), "main"),
				prefs.getString(getString(R.string.key_github_path), "backups/"),
				prefs.getString(getString(R.string.key_github_token), ""));

		if (!exporter.isConfigured()) {
			Toast.makeText(getActivity(), R.string.toast_github_not_configured, Toast.LENGTH_LONG).show();
			return;
		}
		String bookName = BooksDbAdapter.getInstance().getActiveBookDisplayName();
		String fileName = Exporter.buildExportFilename(ExportFormat.XML, bookName);
		new GitHubExportTask(this, preference, exporter, fileName).execute();
	}

	/**
	 * Builds the backup and uploads it to GitHub off the UI thread.
	 * Static with weak references so it does not leak the fragment.
	 */
	private static class GitHubExportTask extends AsyncTask<Void, Void, String> {
		private final WeakReference<Preference> mPreferenceRef;
		private final Context mAppContext;
		private final GitHubExporter mExporter;
		private final String mFileName;

		GitHubExportTask(BackupPreferenceFragment fragment, Preference preference,
						 GitHubExporter exporter, String fileName) {
			mPreferenceRef = new WeakReference<>(preference);
			mAppContext = fragment.getActivity().getApplicationContext();
			mExporter = exporter;
			mFileName = fileName;
		}

		@Override
		protected void onPreExecute() {
			Preference preference = mPreferenceRef.get();
			if (preference != null)
				preference.setEnabled(false);
		}

		/**
		 * @return {@code null} on success, otherwise the error message
		 */
		@Override
		protected String doInBackground(Void... params) {
			try {
				mExporter.upload(mFileName, BackupManager.createActiveBookBackupBytes());
				return null;
			} catch (IOException | RuntimeException e) {
				Log.e(LOG_TAG, "GitHub export failed", e);
				return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
			}
		}

		@Override
		protected void onPostExecute(String error) {
			Preference preference = mPreferenceRef.get();
			if (preference != null)
				preference.setEnabled(true);

			if (error == null)
				Toast.makeText(mAppContext, R.string.toast_github_export_successful, Toast.LENGTH_SHORT).show();
			else
				Toast.makeText(mAppContext,
						mAppContext.getString(R.string.toast_github_export_failed, error),
						Toast.LENGTH_LONG).show();
		}
	}

	/**
	 * Creates a backup of the active book on a background thread and reports the result
	 * with a toast once it has finished.
	 * <p>Declared static and holding only weak references so that a running backup
	 * does not leak the fragment if the user leaves the screen.</p>
	 */
	private static class CreateBackupTask extends AsyncTask<Void, Void, Boolean> {
		private final WeakReference<BackupPreferenceFragment> mFragmentRef;
		private final WeakReference<Preference> mPreferenceRef;
		private final Context mAppContext;

		CreateBackupTask(BackupPreferenceFragment fragment, Preference preference) {
			mFragmentRef = new WeakReference<>(fragment);
			mPreferenceRef = new WeakReference<>(preference);
			mAppContext = fragment.getActivity().getApplicationContext();
		}

		@Override
		protected void onPreExecute() {
			// prevent a second backup from being started while this one is running
			Preference preference = mPreferenceRef.get();
			if (preference != null)
				preference.setEnabled(false);
		}

		@Override
		protected Boolean doInBackground(Void... params) {
			return BackupManager.backupActiveBook();
		}

		@Override
		protected void onPostExecute(Boolean result) {
			Preference preference = mPreferenceRef.get();
			if (preference != null)
				preference.setEnabled(true);

			int msg = result ? R.string.toast_backup_successful : R.string.toast_backup_failed;
			Toast.makeText(mAppContext, msg, Toast.LENGTH_SHORT).show();
		}
	}

	/**
     * Listens for changes to the preference and sets the preference summary to the new value
     * @param preference Preference which has been changed
     * @param newValue New value for the changed preference
     * @return <code>true</code> if handled, <code>false</code> otherwise
     */
	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		if (preference.getKey().equals(getString(R.string.key_github_repo))
				|| preference.getKey().equals(getString(R.string.key_github_branch))
				|| preference.getKey().equals(getString(R.string.key_github_path))
				|| preference.getKey().equals(getString(R.string.key_github_token))) {
			preference.setSummary(summaryForGitHubPreference(preference.getKey(), newValue.toString()));
			return true;
		}

		preference.setSummary(newValue.toString());
		if (preference.getKey().equals(getString(R.string.key_default_currency))){
			GnuCashApplication.setDefaultCurrencyCode(newValue.toString());
		}
		
		if (preference.getKey().equals(getString(R.string.key_default_export_email))){
			String emailSetting = newValue.toString();
			if (emailSetting == null || emailSetting.trim().isEmpty()){
				preference.setSummary(R.string.summary_default_export_email);
			}					
		}

        if (preference.getKey().equals(getString(R.string.key_default_export_format))){
            String exportFormat = newValue.toString();
            if (exportFormat == null || exportFormat.trim().isEmpty()){
                preference.setSummary(R.string.summary_default_export_format);
            }
        }
		return true;
	}



	/**
	 * Toggles the checkbox of the GoogleDrive Sync preference if a Google Drive account is linked
	 * @param pref Google Drive Sync preference
	 */
	public void toggleGoogleDrivePreference(Preference pref){
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
		String appFolderId = sharedPreferences.getString(getString(R.string.key_google_drive_app_folder_id),null);
		((CheckBoxPreference)pref).setChecked(appFolderId != null);
	}


	/**
	 * Toggles synchronization with Google Drive on or off
	 */
	private void toggleGoogleDriveSync(){
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
		final String appFolderId = sharedPreferences.getString(getString(R.string.key_google_drive_app_folder_id), null);
		if (appFolderId != null){
			sharedPreferences.edit().remove(getString(R.string.key_google_drive_app_folder_id)).commit(); //commit (not apply) because we need it to be saved *now*
			mGoogleApiClient.disconnect();
		} else {
			mGoogleApiClient.connect();
		}
	}

	public static GoogleApiClient getGoogleApiClient(final Context context) {
		return new GoogleApiClient.Builder(context)
				.addApi(Drive.API)
				.addScope(Drive.SCOPE_APPFOLDER)
				.addScope(Drive.SCOPE_FILE)
				.addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
					@Override
					public void onConnected(Bundle bundle) {
						SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
						String appFolderId = sharedPreferences.getString(context.getString(R.string.key_google_drive_app_folder_id), null);
						if (appFolderId == null) {
							MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
									.setTitle(context.getString(R.string.app_name)).build();
							Drive.DriveApi.getRootFolder(mGoogleApiClient).createFolder(
									mGoogleApiClient, changeSet).setResultCallback(new ResultCallback<DriveFolder.DriveFolderResult>() {
								@Override
								public void onResult(DriveFolder.DriveFolderResult result) {
									if (!result.getStatus().isSuccess()) {
										Log.e(LOG_TAG, "Error creating the application folder");
										return;
									}

									String folderId = result.getDriveFolder().getDriveId().toString();
									PreferenceManager.getDefaultSharedPreferences(context)
											.edit().putString(context.getString(R.string.key_google_drive_app_folder_id),
											folderId).commit(); //commit because we need it to be saved *now*
								}
							});

						}
						Toast.makeText(context, R.string.toast_connected_to_google_drive, Toast.LENGTH_SHORT).show();
					}

					@Override
					public void onConnectionSuspended(int i) {
						Toast.makeText(context, "Connection to Google Drive suspended!", Toast.LENGTH_LONG).show();
					}
				})
				.addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
					@Override
					public void onConnectionFailed(ConnectionResult connectionResult) {
						Log.e(PreferenceActivity.class.getName(), "Connection to Google Drive failed");
						if (connectionResult.hasResolution() && context instanceof Activity) {
							try {
								Log.e(BackupPreferenceFragment.class.getName(), "Trying resolution of Google API connection failure");
								connectionResult.startResolutionForResult((Activity) context, REQUEST_RESOLVE_CONNECTION);
							} catch (IntentSender.SendIntentException e) {
								Log.e(BackupPreferenceFragment.class.getName(), e.getMessage());
								Toast.makeText(context, R.string.toast_unable_to_connect_to_google_drive, Toast.LENGTH_LONG).show();
							}
						} else {
							if (context instanceof Activity)
								GooglePlayServicesUtil.getErrorDialog(connectionResult.getErrorCode(), (Activity) context, 0).show();
						}
					}
				})
				.build();
	}

	/**
	 * Opens a dialog for a user to select a backup to restore and then restores the backup
	 */
	private void restoreBackup() {
		Log.i("Settings", "Opening GnuCash XML backups for restore");
		final String bookUID = BooksDbAdapter.getInstance().getActiveBookUID();

		final String defaultBackupFile = BackupManager.getBookBackupFileUri(bookUID);
		if (defaultBackupFile != null){
			android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(getActivity())
					.setTitle(R.string.title_confirm_restore_backup)
					.setMessage(R.string.msg_confirm_restore_backup_into_new_book)
					.setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
					})
					.setPositiveButton(R.string.btn_restore, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialogInterface, int i) {
							new ImportAsyncTask(getActivity()).execute(Uri.parse(defaultBackupFile));
						}
					});
			builder.create().show();
			return; //stop here if the default backup file exists
		}

		//If no default location was set, look in the internal SD card location
		if (BackupManager.getBackupList(bookUID).isEmpty()){
			android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(getActivity())
					.setTitle(R.string.title_no_backups_found)
					.setMessage(R.string.msg_no_backups_to_restore_from)
					.setNegativeButton(R.string.label_dismiss, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
					});
			builder.create().show();
			return;
		}


		final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.select_dialog_singlechoice);
		final DateFormat dateFormatter = SimpleDateFormat.getDateTimeInstance();
		for (File backupFile : BackupManager.getBackupList(bookUID)) {
			long time = Exporter.getExportTime(backupFile.getName());
			if (time > 0)
				arrayAdapter.add(dateFormatter.format(new Date(time)));
			else //if no timestamp was found in the filename, just use the name
				arrayAdapter.add(backupFile.getName());
		}

		AlertDialog.Builder restoreDialogBuilder =  new AlertDialog.Builder(getActivity());
		restoreDialogBuilder.setTitle(R.string.title_select_backup_to_restore);
		restoreDialogBuilder.setNegativeButton(R.string.alert_dialog_cancel,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				});
		restoreDialogBuilder.setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				File backupFile = BackupManager.getBackupList(bookUID).get(which);
				new ImportAsyncTask(getActivity()).execute(Uri.fromFile(backupFile));
			}
		});

		restoreDialogBuilder.create().show();
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode){

			case REQUEST_RESOLVE_CONNECTION:
				if (resultCode == Activity.RESULT_OK) {
					mGoogleApiClient.connect();
				}
				break;

			case REQUEST_BACKUP_FILE:
				if (resultCode == Activity.RESULT_OK){
					Uri backupFileUri = null;
					if (data != null){
						backupFileUri = data.getData();
					}
					if (backupFileUri == null)
						break;

					final int takeFlags = data.getFlags()
							& (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
					getActivity().getContentResolver().takePersistableUriPermission(backupFileUri, takeFlags);

					// commit (not apply): the backup task below reads this value right away
					PreferenceActivity.getActiveBookSharedPreferences()
							.edit()
							.putString(BackupManager.KEY_BACKUP_FILE, backupFileUri.toString())
							.commit();

					Preference pref = findPreference(getString(R.string.key_backup_location));
					pref.setSummary(backupFileUri.getAuthority());

					// The system file picker only creates an EMPTY document. Nothing is written
					// to it until the next backup, so write the first backup now.
					new CreateBackupTask(this, pref).execute();
				}
				break;
		}
	}
}
