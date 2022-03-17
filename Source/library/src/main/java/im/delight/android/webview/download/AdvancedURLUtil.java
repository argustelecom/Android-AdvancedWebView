package im.delight.android.webview.download;

import android.webkit.URLUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extension of {@link android.webkit.URLUtil}.
 * Inspired by mozilla-mobile/android-components.
 *
 * @see mozilla-mobile/android-components https://github.com/mozilla-mobile/android-components/blob/main/components/support/utils/src/main/java/mozilla/components/support/utils/DownloadUtils.kt
 */
public final class AdvancedURLUtil {

	/**
	 * This is the regular expression to match the content disposition type segment.
	 *
	 * A content disposition header can start either with inline or attachment followed by comma;
	 *  For example: attachment; filename="filename.jpg" or inline; filename="filename.jpg"
	 * (inline|attachment)\\s*; -> Match either inline or attachment, followed by zero o more
	 * optional whitespaces characters followed by a comma.
	 * originals: https://github.com/mozilla-mobile/android-components/blob/main/components/support/utils/src/main/java/mozilla/components/support/utils/DownloadUtils.kt
	 */
	private static final String CONTENT_DISPOSITION_TYPE = "(inline|attachment)\\s*;";

	/**
	 * This is the regular expression to match filename* parameter segment.
	 *
	 * A content disposition header could have an optional filename* parameter,
	 * the difference between this parameter and the filename is that this uses
	 * the encoding defined in RFC 5987.
	 *
	 * Some examples:
	 *  filename*=utf-8''success.html
	 *  filename*=iso-8859-1'en'file%27%20%27name.jpg
	 *  filename*=utf-8'en'filename.jpg
	 *
	 * For matching this section we use:
	 * \\s*filename\\s*=\\s*= -> Zero or more optional whitespaces characters
	 * followed by filename followed by any zero or more whitespaces characters and the equal sign;
	 *
	 * (utf-8|iso-8859-1)-> Either utf-8 or iso-8859-1 encoding types.
	 *
	 * '[^']*'-> Zero or more characters that are inside of single quotes '' that are not single
	 * quote.
	 *
	 * (\S*) -> Zero or more characters that are not whitespaces. In this group,
	 * it's where we are going to have the filename.
	 *
	 * originals: https://github.com/mozilla-mobile/android-components/blob/main/components/support/utils/src/main/java/mozilla/components/support/utils/DownloadUtils.kt
	 */
	private static final String CONTENT_DISPOSITION_FILE_NAME_ASTERISK =
			"\\s*filename\\*\\s*=\\s*(utf-8|iso-8859-1)'[^']*'([^;\\s]*)";

	private static final Pattern CONTENT_DISPOSITION_PATTERN_FILENAME_ASTERISK =
			Pattern.compile(CONTENT_DISPOSITION_TYPE + CONTENT_DISPOSITION_FILE_NAME_ASTERISK,
					Pattern.CASE_INSENSITIVE);


	/**
	 * URLUtil#guessFileName, that supports RFC 5987 format contentDisposition header value,
	 * like <code>filename*=utf-8''success.html</code>.
	 * @return suggested filename
	 */
	public static String guessFileName(String url, String contentDisposition, String mimeType){
		contentDisposition = convertContentDispositionWithFileNameAsteriskToWithoutAsterisk(contentDisposition);
		return URLUtil.guessFileName(url, contentDisposition, mimeType);
	}

	// inspired by https://github.com/mozilla-mobile/android-components/blob/main/components/support/utils/src/main/java/mozilla/components/support/utils/DownloadUtils.kt
	private static String convertContentDispositionWithFileNameAsteriskToWithoutAsterisk(String contentDisposition) {
		if (contentDisposition == null){
			return null;
		}

		Matcher m = CONTENT_DISPOSITION_PATTERN_FILENAME_ASTERISK.matcher(contentDisposition);
		if (m.find()) {
			String type = m.group(1);
			//String encoding = m.group(2);
			String fileName = m.group(3);
			return type + ";filename=\"" + fileName + "\"";
		}
		return contentDisposition;
	}

}
