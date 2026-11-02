package clre20.onlineReward.update;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 檢查 GitHub 最新正式 Release（/releases/latest 不含草稿與預覽版）。
 * 結果快取 10 分鐘（失敗 1 分鐘），不定時輪詢；同時多個請求只會送出一次。
 * 所有網路工作都在傳入的單一執行緒上。
 */
public final class UpdateChecker {

    public enum Status { NEW, LATEST, DEV, NONE, FAILED }

    public record Result(Status status, String current, String latest, String url, String error) {
    }

    private static final String DEFAULT_API = "https://api.github.com/repos/clre20/OnlineReward/releases/latest";
    private static final String RELEASES_URL = "https://github.com/clre20/OnlineReward/releases/latest";
    private static final long CACHE_MS = 10 * 60_000L;
    private static final long FAIL_CACHE_MS = 60_000L;
    private static final int MAX_BODY = 512 * 1024;
    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]{1,64})\"");
    private static final Pattern URL = Pattern.compile("\"html_url\"\\s*:\\s*\"(https://github\\.com/[^\"\\s]{1,200})\"");

    private final String current;
    private final String api;
    private final ExecutorService executor;

    private Result cached;
    private long cachedUntil;
    private CompletableFuture<Result> inFlight;

    public UpdateChecker(String current, ExecutorService executor) {
        this.current = current;
        this.executor = executor;
        this.api = System.getProperty("onlinereward.update-api", DEFAULT_API);
    }

    /** 回傳結果（快取有效時直接用快取）。完成回呼在網路執行緒上，呼叫端要自己切回主執行緒。 */
    public synchronized CompletableFuture<Result> check() {
        long now = System.currentTimeMillis();
        if (cached != null && now < cachedUntil) {
            return CompletableFuture.completedFuture(cached);
        }
        if (inFlight != null) {
            return inFlight;
        }
        CompletableFuture<Result> f = CompletableFuture.supplyAsync(this::fetch, executor);
        inFlight = f;
        f.whenComplete((r, t) -> {
            synchronized (UpdateChecker.this) {
                Result res = r != null ? r : new Result(Status.FAILED, current, null, RELEASES_URL,
                        t == null ? "unknown" : t.getClass().getSimpleName());
                cached = res;
                cachedUntil = System.currentTimeMillis()
                        + (res.status() == Status.FAILED ? FAIL_CACHE_MS : CACHE_MS);
                inFlight = null;
            }
        });
        return f;
    }

    private Result fetch() {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(api).toURL().openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("User-Agent", "OnlineReward/" + current);
            int code = conn.getResponseCode();
            if (code == 404) {
                return new Result(Status.NONE, strip(current), null, RELEASES_URL, null);
            }
            if (code != 200) {
                return new Result(Status.FAILED, strip(current), null, RELEASES_URL, "HTTP " + code);
            }
            String body;
            try (InputStream in = conn.getInputStream()) {
                body = new String(in.readNBytes(MAX_BODY), StandardCharsets.UTF_8);
            }
            Matcher m = TAG.matcher(body);
            if (!m.find()) {
                return new Result(Status.FAILED, strip(current), null, RELEASES_URL, "no tag_name");
            }
            String latest = strip(m.group(1));
            Matcher u = URL.matcher(body);
            String url = u.find() ? u.group(1) : RELEASES_URL;
            int cmp = compare(strip(current), latest);
            Status s = cmp < 0 ? Status.NEW : cmp > 0 ? Status.DEV : Status.LATEST;
            return new Result(s, strip(current), latest, url, null);
        } catch (Exception e) {
            return new Result(Status.FAILED, strip(current), null, RELEASES_URL, e.getClass().getSimpleName());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    static String strip(String v) {
        v = v.trim();
        return (v.startsWith("v") || v.startsWith("V")) ? v.substring(1) : v;
    }

    /** 逐段比數字（1.10.0 &gt; 1.9.9）；非數字部分忽略。 */
    static int compare(String a, String b) {
        List<Long> x = parts(a);
        List<Long> y = parts(b);
        for (int i = 0; i < Math.max(x.size(), y.size()); i++) {
            long p = i < x.size() ? x.get(i) : 0;
            long q = i < y.size() ? y.get(i) : 0;
            if (p != q) {
                return Long.compare(p, q);
            }
        }
        return 0;
    }

    private static List<Long> parts(String v) {
        List<Long> out = new ArrayList<>();
        for (String s : strip(v).split("[.\\-+]")) {
            String digits = s.replaceAll("[^0-9]", "");
            if (digits.isEmpty()) {
                continue;
            }
            try {
                out.add(Long.parseLong(digits.length() > 18 ? digits.substring(0, 18) : digits));
            } catch (NumberFormatException e) {
                out.add(0L);
            }
        }
        return out;
    }
}
