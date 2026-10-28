/*
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

package org.gnucash.android.export;

import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Uploads a file to a GitHub repository using the
 * <a href="https://docs.github.com/en/rest/repos/contents#create-or-update-file-contents">
 * "create or update file contents"</a> REST endpoint.
 * <p>Must be called from a background thread.</p>
 */
public class GitHubExporter {

    private static final String API_URL = "https://api.github.com/repos/%s/contents/%s";
    private static final int TIMEOUT_MS = 30000;

    /**
     * Location of the password-encrypted access token (raw content of
     * https://github.com/definitly486/gnucash/blob/main/token.enc)
     */
    public static final String TOKEN_FILE_URL =
            "https://raw.githubusercontent.com/definitly486/gnucash/main/token.enc";
    private static final int MAX_TOKEN_FILE_BYTES = 64 * 1024;

    /**
     * Downloads the encrypted token file. Must be called from a background thread.
     *
     * @return the raw file content
     * @throws IOException if the download failed
     */
    public static byte[] downloadTokenFile() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(TOKEN_FILE_URL).openConnection();
        try {
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK)
                throw new IOException("HTTP " + code + " while downloading the token file");

            InputStream in = connection.getInputStream();
            try {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[2048];
                int n;
                while ((n = in.read(chunk)) != -1) {
                    buffer.write(chunk, 0, n);
                    if (buffer.size() > MAX_TOKEN_FILE_BYTES)
                        throw new IOException("Token file is unexpectedly large");
                }
                return buffer.toByteArray();
            } finally {
                in.close();
            }
        } finally {
            connection.disconnect();
        }
    }

    private final String mRepo;
    private final String mBranch;
    private final String mFolder;
    private final String mToken;

    /**
     * @param repo   repository in the form {@code owner/repo}
     * @param branch branch to commit to (the repository default branch if empty)
     * @param folder folder inside the repository, may be empty
     * @param token  personal access token with write access to the repository
     */
    public GitHubExporter(String repo, String branch, String folder, String token) {
        mRepo = normalizeRepo(repo);
        mBranch = branch == null ? "" : branch.trim();
        mFolder = normalizeFolder(folder);
        mToken = token == null ? "" : token.trim();
    }

    /**
     * Accepts {@code owner/repo} as well as a pasted github.com URL.
     */
    static String normalizeRepo(String repo) {
        String r = repo == null ? "" : repo.trim();
        r = r.replaceFirst("^https?://github\\.com/", "");
        r = r.replaceFirst("\\.git$", "");
        r = r.replaceAll("^/+|/+$", "");
        return r;
    }

    static String normalizeFolder(String folder) {
        String f = folder == null ? "" : folder.trim();
        f = f.replaceAll("^/+", "");
        if (!f.isEmpty() && !f.endsWith("/"))
            f += "/";
        return f;
    }

    /**
     * @return {@code true} if the repository looks like {@code owner/repo} and a token is set
     */
    public boolean isConfigured() {
        return mRepo.matches("[^/\\s]+/[^/\\s]+") && !mToken.isEmpty();
    }

    /**
     * Commits {@code content} as a new file {@code <folder><fileName>} on the configured branch.
     *
     * @param fileName name of the file to create
     * @param content  file content
     * @throws IOException if the request failed; the message contains the GitHub reason
     */
    public void upload(String fileName, byte[] content) throws IOException {
        String path = mFolder + fileName;
        // encode each path segment but keep the slashes
        String encodedPath = java.net.URLEncoder.encode(path, "UTF-8")
                .replace("%2F", "/").replace("+", "%20");
        URL url = new URL(String.format(API_URL, mRepo, encodedPath));

        JSONObject body = new JSONObject();
        try {
            body.put("message", "GnuCash Android backup " + fileName);
            body.put("content", Base64.encodeToString(content, Base64.NO_WRAP));
            if (!mBranch.isEmpty())
                body.put("branch", mBranch);
        } catch (JSONException e) {
            throw new IOException(e);
        }
        byte[] payload = body.toString().getBytes("UTF-8");

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setRequestMethod("PUT");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(payload.length);
            connection.setRequestProperty("Authorization", "Bearer " + mToken);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");

            OutputStream out = connection.getOutputStream();
            try {
                out.write(payload);
            } finally {
                out.close();
            }

            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_CREATED) {
                throw new IOException("HTTP " + code + ": " + readGitHubMessage(connection));
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String readGitHubMessage(HttpURLConnection connection) {
        InputStream in = connection.getErrorStream();
        if (in == null)
            return "no response body";
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[2048];
            int n;
            while ((n = in.read(chunk)) != -1)
                buffer.write(chunk, 0, n);
            String text = buffer.toString("UTF-8");
            try {
                return new JSONObject(text).optString("message", text);
            } catch (JSONException e) {
                return text;
            }
        } catch (IOException e) {
            return "unreadable response";
        } finally {
            try { in.close(); } catch (IOException ignored) {}
        }
    }
}
