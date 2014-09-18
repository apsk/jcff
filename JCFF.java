package rgtbctltpx.jcff;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class CloudFlareFucker {
    private static final String[] USER_AGENTS = {
            // Chrome
            "Mozilla/5.0 (Windows NT 6.3; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/37.0.2049.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/36.0.1985.67 Safari/537.36",
            "Mozilla/5.0 (Windows NT 5.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/36.0.1985.67 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/36.0.1944.0 Safari/537.36",
            // Firefox
            "Mozilla/5.0 (Windows NT 5.1; rv:31.0) Gecko/20100101 Firefox/31.0",
            "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:29.0) Gecko/20120101 Firefox/29.0",
            "Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:25.0) Gecko/20100101 Firefox/29.0",
            "Mozilla/5.0 (X11; OpenBSD amd64; rv:28.0) Gecko/20100101 Firefox/28.0",
            // Opera
            "Opera/9.80 (Windows NT 6.0) Presto/2.12.388 Version/12.14",
            "Opera/12.80 (Windows NT 5.1; U; en) Presto/2.10.289 Version/12.02",
            "Opera/9.80 (Windows NT 6.1; U; es-ES) Presto/2.9.181 Version/12.00",
            "Opera/12.0(Windows NT 5.2;U;en)Presto/22.9.168 Version/12.00",
            // Internet Explorer
            "Mozilla/5.0 (compatible; MSIE 10.0; Windows NT 6.1; WOW64; Trident/6.0)",
            "Mozilla/5.0 (Windows; U; MSIE 9.0; Windows NT 9.0; en-US))",
            "Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.1; WOW64; Trident/5.0; SLCC2; Media Center PC 6.0; InfoPath.3; MS-RTC LM 8; Zune 4.7)",
            "Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.1; Win64; x64; Trident/5.0; .NET CLR 2.0.50727; SLCC2; .NET CLR 3.5.30729; .NET CLR 3.0.30729; Media Center PC 6.0; Zune 4.0; Tablet PC 2.0; InfoPath.3; .NET4.0C; .NET4.0E)"
    };

    private static final Pattern
        JSCHL_VC_REGEX = Pattern.compile("name=\"jschl_vc\" value=\"(\\w+)\""),
        ANSWER_CB_REGEX = Pattern.compile("setTimeout.+?\\r?\\n([\\s\\S]+?a\\.value =.+?)\\+");

    private final URL url;
    private final String userAgent;
    private String cookie;

    private CloudFlareFucker(URL url) {
        this.url = url;
        this.userAgent = USER_AGENTS[((int)(Math.random() * 100)) % 16];
    }

    public static CloudFlareFucker forUrl(URL url) {
        return new CloudFlareFucker(url);
    }

    public static CloudFlareFucker forUrl(String url) throws MalformedURLException {
        return new CloudFlareFucker(new URL(url));
    }

    public HttpURLConnection openConnection(String relativePath) throws MalformedURLException {
        URL url = new URL(this.url, relativePath);
        try {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn = followRedirects(setupConnection(conn));
            if (conn.getResponseCode() == HttpURLConnection.HTTP_UNAVAILABLE) {
                String html = consumeToEnd(conn.getErrorStream());
                String challenge = findFirstMatch(JSCHL_VC_REGEX, html).group(1);
                String callback = findFirstMatch(ANSWER_CB_REGEX, html).group(1).replace("a.value =", "");
                String[] lines = callback.split("\\r?\\n");
                callback = lines[0] + lines[lines.length - 1];
                ScriptEngine scriptEngine = new ScriptEngineManager().getEngineByName("JavaScript");
                Double cbResult = (Double) scriptEngine.eval(callback);
                String host = this.url.getHost();
                int answer = cbResult.intValue() + host.length();
                String cookie = conn.getHeaderField("Set-Cookie");
                cookie = cookie.substring(0, cookie.indexOf(';'));
                this.cookie = cookie;
                URL cfCheckUrl = new URL(String.format(
                    "https://%s/cdn-cgi/l/chk_jschl?jschl_vc=%s&jschl_answer=%s",
                    host, challenge, answer
                ));
                conn = (HttpURLConnection) cfCheckUrl.openConnection();
                setupConnection(conn);
                conn.setRequestProperty("Referer", url.toString());
                cookie = conn.getHeaderField("Set-Cookie");
                cookie = cookie.substring(0, cookie.indexOf(';'));
                this.cookie += "; " + cookie;
                conn = followRedirects(conn);
            }
            return conn;
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        catch (ScriptException e) {
            throw new RuntimeException("Answer calculation code extracted from page doesn't compute!");
        }
    }

    public InputStream getPageStream(String relativePath) throws IOException {
       return openConnection(relativePath).getInputStream();
    }

    public String getPageContents(String relativePath) throws IOException {
        return consumeToEnd(getPageStream(relativePath));
    }

    private HttpURLConnection setupConnection(HttpURLConnection conn) {
        conn.setRequestProperty("User-Agent", userAgent);
        conn.setRequestProperty("Cookie", cookie);
        return conn;
    }

    private HttpURLConnection followRedirects(HttpURLConnection conn) throws IOException {
        while (isRedirection(conn.getResponseCode())) {
            URL url = new URL(conn.getHeaderField("Location"));
            conn = (HttpURLConnection) url.openConnection();
            setupConnection(conn);
        }
        return conn;
    }

    private static Matcher findFirstMatch(Pattern pattern, CharSequence input) {
        Matcher matcher = pattern.matcher(input);
        matcher.find();
        return matcher;
    }

    private static String consumeToEnd(InputStream stream) {
        return new BufferedReader(new InputStreamReader(stream))
            .lines().collect(Collectors.joining("\n"));
    }

    private static boolean isRedirection(int code) {
        return code >= 300 && code < 400;
    }
}