package im.delight.android.webview.download;

import static android.app.DownloadManager.STATUS_FAILED;
import static android.app.DownloadManager.STATUS_SUCCESSFUL;

import android.app.DownloadManager;
import android.app.DownloadManager.Request;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Simplified API for DownloadManager.
 * <p>
 * Create SimpleDownloadManager with {@link Context} and {@link Listener}.
 * <p>
 * Schedule new download via {@link #enqueue(String, String, Map)}.
 * When download will be completed then the  {@link Listener#onDownloadComplete} callback
 * will be invoked.
 * <p>
 * Call {@link #destroy()} before context  destroyed.
 */
public class SimpleDownloadManager {
	private final Context context;
	private volatile BroadcastReceiver onDownloadComplete;
	private final Listener listener;
	private final Set<Download> enqueuedDownloads = new HashSet<>();
	private final DownloadManager downloadManager;

	public SimpleDownloadManager(Context context, Listener listener) {
		this.context = context;
		this.listener = listener;
		downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
	}

	public void enqueue(final String fromUrl, final String toFilename,
			Map<String, String> requestHeaders) throws SystemServiceDisabledException {

		final Request request = new Request(Uri.parse(fromUrl));
		if (requestHeaders != null) {
			for (Map.Entry<String, String> header : requestHeaders.entrySet()) {
				request.addRequestHeader(header.getKey(), header.getValue());
			}
		}
		if (Build.VERSION.SDK_INT >= 11) {
			request.allowScanningByMediaScanner();
			request.setNotificationVisibility(
					DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
		}
		request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, toFilename);
		request.setTitle(toFilename);

		try {
			synchronized (enqueuedDownloads) {
				if (!isListeningDownloads()) {
					startListeningDownloads();
				}

				long requestId;
				try {
					requestId = downloadManager.enqueue(request);
				} catch (SecurityException e) {
					if (Build.VERSION.SDK_INT >= 11) {
						request.setNotificationVisibility(Request.VISIBILITY_VISIBLE);
					}
					requestId = downloadManager.enqueue(request);
				}
				enqueuedDownloads.add(new Download(requestId, toFilename, fromUrl));
			}
		} catch (IllegalArgumentException e) {
			throw new SystemServiceDisabledException(e);

		}
	}

	private boolean isListeningDownloads() {
		return onDownloadComplete != null;
	}

	private void startListeningDownloads() {
		checkState(!isListeningDownloads());

		onDownloadComplete = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				onDownloadComplete(intent);
			}
		};
		context.registerReceiver(onDownloadComplete,
				new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

	}

	private void stopListeningDownloads() {
		if (onDownloadComplete != null) {
			context.unregisterReceiver(onDownloadComplete);
			onDownloadComplete = null;
		}
	}

	public void destroy() {
		synchronized (enqueuedDownloads) {
			stopListeningDownloads();
		}
	}

	private void onDownloadComplete(Intent intent) {
		// will only ever be called on the main application thread

		// we can fetch the download id received with the broadcast
		// long completedDownloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
		// but download may be removed.
		// Will be ACTION_DOWNLOAD_COMPLETE invoked?  DownloadManager documentation is no clear.
		// So we need check all enqueuedDownloads to prevent a memory leak.
		Set<Download> completedDownloads = new HashSet<>();
		synchronized (enqueuedDownloads) {
			for (Download download : enqueuedDownloads) {
				try {
					Download completedDownload = fetchDownloadIfCompleted(download);
					if (completedDownload != null) {
						completedDownloads.add(completedDownload);
					}
				} catch (DownloadNotFoundException e) {
					download.success = false;
					download.failReason = null;
					completedDownloads.add(download);
				}
			}
			enqueuedDownloads.removeAll(completedDownloads);
			if (enqueuedDownloads.isEmpty()) {
				stopListeningDownloads();
			}
		}

		for (Download download : completedDownloads) {
			listener.onDownloadFinish(download);
		}
	}

	private Download fetchDownloadIfCompleted(Download download) throws DownloadNotFoundException {
		Cursor c = downloadManager.query(new DownloadManager.Query().setFilterById(download.id));
		if (!c.moveToFirst()) {
			throw new DownloadNotFoundException();
		}

		int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
		if (status != STATUS_FAILED && status != STATUS_SUCCESSFUL) {
			return null;
		}

		download.success = status != STATUS_FAILED;
		if (!download.success) {
			download.failReason = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_REASON));
		}
		if (Build.VERSION.SDK_INT < 24) {
			download.localFileName = c
					.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME));
		}
		download.localUri = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
		download.totalSizeBytes = c
				.getLong(c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
		return download;
	}

	private void checkState(boolean expression) {
		if (!expression) {
			throw new IllegalStateException();
		}
	}

	public interface Listener {
		void onDownloadFinish(CompletedDownload download);
	}

	public static class SystemServiceDisabledException extends Exception {
		public SystemServiceDisabledException(Throwable throwable) {
			super(throwable);
		}
	}

	private static class DownloadNotFoundException extends Exception {
	}

	public static class Download implements CompletedDownload {
		private final long id;
		private final String fileName;
		private final String uri;
		private String localFileName;
		private String localUri;
		private long totalSizeBytes;

		private boolean success;
		private Integer failReason;

		private Download(long id, String fileName, String uri) {
			this.id = id;
			this.fileName = fileName;
			this.uri = uri;
		}

		public String getFileName() {
			return fileName;
		}

		public String getUri() {
			return uri;
		}

		public String getLocalFileName() {
			return localFileName;
		}

		public String getLocalUri() {
			return localUri;
		}

		public long getTotalSizeBytes() {
			return totalSizeBytes;
		}

		public boolean isSuccess() {
			return success;
		}

		public Integer getFailReason() {
			return failReason;
		}
	}
}
