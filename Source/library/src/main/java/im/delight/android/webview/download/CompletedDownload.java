package im.delight.android.webview.download;

/**
 * File download, that completed successfully or failed.
 */
public interface CompletedDownload {

	/**
	 * URI downloaded.
	 */
	String getUri();

	/**
	 * fileName, passed to handleDownload.
	 */
	String getFileName();

	/**
	 * is download success or failed/removed.
	 */
	boolean isSuccess();

	/**
	 * null when download success or removed. When failed with http error this will hold the HTTP
	 * status code. For other errors, it will hold one of the
	 * DownloadManager.ERROR_* constants.
	 */
	Integer getFailReason();

	/**
	 * Path to the downloaded file on disk. Null when Build.VERSION.SDK_INT >= 24.
	 * use {@link #getLocalUri()} instead.
	 */
	String getLocalFileName();

	/**
	 * Uri where downloaded file is stored.
	 */
	String getLocalUri();

	/**
	 * size of the downloaded file in bytes
	 */
	long getTotalSizeBytes();

}
