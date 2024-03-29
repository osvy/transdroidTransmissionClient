/*
 *	This file is part of Transdroid <http://www.transdroid.org>
 *	
 *	Transdroid is free software: you can redistribute it and/or modify
 *	it under the terms of the GNU General Public License as published by
 *	the Free Software Foundation, either version 3 of the License, or
 *	(at your option) any later version.
 *	
 *	Transdroid is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *	GNU General Public License for more details.
 *	
 *	You should have received a copy of the GNU General Public License
 *	along with Transdroid.  If not, see <http://www.gnu.org/licenses/>.
 *	
 */
package org.transdroid.daemon.BuffaloNas;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.ArrayList;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.transdroid.daemon.Daemon;
import org.transdroid.daemon.DaemonException;
import org.transdroid.daemon.DaemonSettings;
import org.transdroid.daemon.IDaemonAdapter;
import org.transdroid.daemon.Priority;
import org.transdroid.daemon.Torrent;
import org.transdroid.daemon.TorrentFile;
import org.transdroid.daemon.TorrentStatus;
import org.transdroid.daemon.DaemonException.ExceptionType;
import org.transdroid.daemon.task.AddByFileTask;
import org.transdroid.daemon.task.AddByUrlTask;
import org.transdroid.daemon.task.DaemonTask;
import org.transdroid.daemon.task.DaemonTaskFailureResult;
import org.transdroid.daemon.task.DaemonTaskResult;
import org.transdroid.daemon.task.DaemonTaskSuccessResult;
import org.transdroid.daemon.task.GetFileListTask;
import org.transdroid.daemon.task.GetFileListTaskSuccessResult;
import org.transdroid.daemon.task.RemoveTask;
import org.transdroid.daemon.task.RetrieveTask;
import org.transdroid.daemon.task.RetrieveTaskSuccessResult;
import org.transdroid.daemon.task.SetTransferRatesTask;
import org.transdroid.daemon.util.DLog;
import org.transdroid.daemon.util.HttpHelper;

import com.android.internalcopy.http.multipart.FilePart;
import com.android.internalcopy.http.multipart.MultipartEntity;
import com.android.internalcopy.http.multipart.Part;

/**
 * The daemon adapter for the Buffalo NAS' integrated torrent client.
 * 
 * @author erickok
 *
 */
public class BuffaloNasAdapter implements IDaemonAdapter {

	private static final String LOG_NAME = "qBittorrent daemon";

	private DaemonSettings settings;
	private DefaultHttpClient httpclient;
	
	public BuffaloNasAdapter(DaemonSettings settings) {
		this.settings = settings;
	}
	
	@Override
	public DaemonTaskResult executeTask(DaemonTask task) {
		
		try {
			switch (task.getMethod()) {
			case Retrieve:

				// Request all torrents from server
				JSONObject result = new JSONObject(makeRequest("/api/torrents-get"));
				return new RetrieveTaskSuccessResult((RetrieveTask) task, parseJsonTorrents(result),null);
				
			case GetFileList:

				// Request files listing for a specific torrent
				String fhash = ((GetFileListTask)task).getTargetTorrent().getUniqueID();
				JSONObject files = new JSONObject(makeRequest("/api/torrent-get-files", new BasicNameValuePair("hash", fhash)));
				return new GetFileListTaskSuccessResult((GetFileListTask) task, parseJsonFiles(files, fhash));
				
			case AddByFile:

				// Upload a local .torrent file
				String ufile = ((AddByFileTask)task).getFile();
				makeUploadRequest("/api/torrent-add?start=yes", ufile);
				return new DaemonTaskSuccessResult(task);

			case AddByUrl:

				// Request to add a torrent by URL
				String url = ((AddByUrlTask)task).getUrl();
				makeRequest("/api/torrent-add", new BasicNameValuePair("url", url), new BasicNameValuePair("start", "yes"));
				return new DaemonTaskSuccessResult(task);

			case Remove:

				// Remove a torrent
				RemoveTask removeTask = (RemoveTask) task;
				makeRequest("/api/torrent-remove", new BasicNameValuePair("hash", removeTask.getTargetTorrent().getUniqueID()), 
						new BasicNameValuePair("delete-torrent", "yes"), new BasicNameValuePair("delete-data", (removeTask.includingData()? "yes": "no")));
				return new DaemonTaskSuccessResult(task);
				
			case Pause:

				// Pause a torrent
				makeRequest("/api/torrent-stop", new BasicNameValuePair("hash", task.getTargetTorrent().getUniqueID()));
				return new DaemonTaskSuccessResult(task);
				
			case Resume:

				// Resume a torrent
				makeRequest("/api/torrent-start", new BasicNameValuePair("hash", task.getTargetTorrent().getUniqueID()));
				return new DaemonTaskSuccessResult(task);
				
			case SetTransferRates:

				// Request to set the maximum transfer rates
				SetTransferRatesTask ratesTask = (SetTransferRatesTask) task;
				String dl = Integer.toString((ratesTask.getDownloadRate() == null? -1: ratesTask.getDownloadRate().intValue() * 1024));
				String ul = Integer.toString((ratesTask.getUploadRate() == null? -1: ratesTask.getUploadRate().intValue() * 1024));
				makeRequest("/api/app-settings-set", new BasicNameValuePair("auto_bandwidth_management", "0"), new BasicNameValuePair("max_dl_rate", dl), new BasicNameValuePair("max_ul_rate", ul), new BasicNameValuePair("max_ul_rate_seed", ul));
				return new DaemonTaskSuccessResult(task);
				
			default:
				return new DaemonTaskFailureResult(task, new DaemonException(ExceptionType.MethodUnsupported, task.getMethod() + " is not supported by " + getType()));
			}
		} catch (JSONException e) {
			return new DaemonTaskFailureResult(task, new DaemonException(ExceptionType.ParsingFailed, e.toString()));
		} catch (DaemonException e) {
			return new DaemonTaskFailureResult(task, e);
		}
	}

	private String makeRequest(String url, NameValuePair... params) throws DaemonException {

		try {
			
			// Initialise the HTTP client
			if (httpclient == null) {
				initialise(HttpHelper.DEFAULT_CONNECTION_TIMEOUT);
			}

			// Add the parameters to the query string
			boolean first = true;
			for (NameValuePair param : params) {
				if (first) {
					url += "?";
					first = false;
				} else {
					url += "&";
				}
				url += param.getName() + "=" + param.getValue();
			}

			// Make the request
			HttpResponse response = httpclient.execute(new HttpGet(buildWebUIUrl(url)));
			HttpEntity entity = response.getEntity();
			if (entity != null) {
				
				// Read JSON response
				java.io.InputStream instream = entity.getContent();
				String result = HttpHelper.ConvertStreamToString(instream);
				instream.close();
				
				// Return raw result
				return result;
			}

			DLog.d(LOG_NAME, "Error: No entity in HTTP response");
			throw new DaemonException(ExceptionType.UnexpectedResponse, "No HTTP entity object in response.");

		} catch (UnsupportedEncodingException e) {
			throw new DaemonException(ExceptionType.ConnectionError, e.toString());
		} catch (Exception e) {
			DLog.d(LOG_NAME, "Error: " + e.toString());
			throw new DaemonException(ExceptionType.ConnectionError, e.toString());
		}
		
	}

	private boolean makeUploadRequest(String path, String file) throws DaemonException {

		try {

			// Initialise the HTTP client
			if (httpclient == null) {
				initialise(HttpHelper.DEFAULT_CONNECTION_TIMEOUT);
			}

			// Setup request using POST
			HttpPost httppost = new HttpPost(buildWebUIUrl(path));
			File upload = new File(URI.create(file));
			Part[] parts = { new FilePart("fileEl", upload) };
			httppost.setEntity(new MultipartEntity(parts, httppost.getParams()));
			
			// Make the request
			HttpResponse response = httpclient.execute(httppost);
			return response.getStatusLine().getStatusCode() == HttpStatus.SC_OK;
	
		} catch (FileNotFoundException e) {
			throw new DaemonException(ExceptionType.FileAccessError, e.toString());
		} catch (Exception e) {
			DLog.d(LOG_NAME, "Error: " + e.toString());
			throw new DaemonException(ExceptionType.ConnectionError, e.toString());
		}
		
	}
	
	/**
	 * Instantiates an HTTP client with proper credentials that can be used for all Buffalo NAS requests.
	 * @param connectionTimeout The connection timeout in milliseconds
	 * @throws DaemonException On conflicting or missing settings
	 */
	private void initialise(int connectionTimeout) throws DaemonException {

		httpclient = HttpHelper.createStandardHttpClient(settings, true);
		
	}
	
	/**
	 * Build the URL of the http request from the user settings
	 * @return The URL to request
	 */
	private String buildWebUIUrl(String path) {
		return (settings.getSsl() ? "https://" : "http://") + settings.getAddress() + ":" + settings.getPort() + path;
	}

	private ArrayList<Torrent> parseJsonTorrents(JSONObject response) throws JSONException {
	
		// Parse response
		ArrayList<Torrent> torrents = new ArrayList<Torrent>();
		JSONArray all = response.getJSONArray("torrents");
		for (int i = 0; i < all.length(); i++) {
			JSONObject tor = all.getJSONObject(i);
			int leechers = tor.getInt("peers_connected");
			int seeders = tor.getInt("seeds_connected");
			int known = tor.getInt("peers_total") + tor.getInt("seeds_total");
			long size = tor.getLong("size");
			long sizeDone = tor.getLong("done");
			long sizeUp = tor.getLong("payload_upload");
			int rateUp = tor.getInt("dl_rate");
			int rateDown = tor.getInt("ul_rate");
			// Add the parsed torrent to the list
			torrents.add(new Torrent(
					(long)i,
					tor.getString("hash"),
					tor.getString("caption"),
					parseStatus(tor.getString("state"), tor.getInt("stopped")),
					null,
					rateDown,
					rateUp,
					leechers,
					leechers + seeders,
					known,
					known,
					(rateDown == 0? -1: (int) ((size - sizeDone) / rateDown)),
					sizeDone,
					sizeUp,
					size,
					(float)sizeDone / size,
					(float)tor.getDouble("distributed_copies") / 10,
					null,
					null,
					null,
					null));
		}
		
		// Return the list
		return torrents;
		
	}

	private TorrentStatus parseStatus(String state, int stopped) {
		// Status is given as a descriptive string and an indication if the torrent was stopped/paused
		if (state.equals("downloading")) {
			if (stopped == 1) {
				return TorrentStatus.Paused;
			} else {
				return TorrentStatus.Downloading;
			}
		} else if (state.equals("seeding")) {
			if (stopped == 1) {
				return TorrentStatus.Paused;
			} else {
				return TorrentStatus.Seeding;
			}
		}
		return TorrentStatus.Unknown;
	}

	private ArrayList<TorrentFile> parseJsonFiles(JSONObject response, String hash) throws JSONException {
	
		// Parse response
		ArrayList<TorrentFile> torrentfiles = new ArrayList<TorrentFile>();
		JSONArray all = response.getJSONObject("torrents").getJSONArray(hash);
		for (int i = 0; i < all.length(); i++) {
			JSONObject file = all.getJSONObject(i);
			long size = file.getLong("size");
			long sizeDone = file.getLong("done");
			torrentfiles.add(new TorrentFile(
					"" + file.getInt("id"),
					file.getString("name"),
					file.getString("name"),
					settings.getDownloadDir() + file.getString("name"),
					size,
					sizeDone,
					Priority.Normal));
		}
		
		// Return the list
		return torrentfiles;
		
	}

	@Override
	public Daemon getType() {
		return settings.getType();
	}

	@Override
	public DaemonSettings getSettings() {
		return this.settings;
	}
	
}
